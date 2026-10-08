package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.AccountType;
import com.example.bank.domain.AuditAction;
import com.example.bank.domain.EntryDirection;
import com.example.bank.domain.LedgerEntry;
import com.example.bank.domain.Transaction;
import com.example.bank.domain.TransactionType;
import com.example.bank.dto.AmountRequest;
import com.example.bank.dto.TransactionResponse;
import com.example.bank.dto.TransferRequest;
import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.exception.ResourceNotFoundException;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.LedgerEntryRepository;
import com.example.bank.repository.TransactionRepository;
import com.example.bank.repository.UserRepository;
import com.example.bank.security.AuthenticatedUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Deposits, withdrawals and transfers. Every public method is one database
 * transaction (all-or-nothing) and follows the same steps:
 * <ol>
 *   <li>Check the request shape (key, amount sign).</li>
 *   <li>Lock the customer account(s) with SELECT ... FOR UPDATE, always in
 *       ascending ID order.</li>
 *   <li>Look up the Idempotency-Key. This happens <em>after</em> locking on
 *       purpose: a retry of the same request waits for the original to commit,
 *       then finds it and returns its result instead of moving money twice.</li>
 *   <li>Check business rules (status, currency, funds) against locked data.</li>
 *   <li>Write one transaction row plus balanced DEBIT/CREDIT ledger entries.</li>
 * </ol>
 */
@Service
public class MoneyMovementService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final BankMetrics metrics;
    private final MoneyRules rules;

    public MoneyMovementService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository,
                                TransactionRepository transactionRepository, UserRepository userRepository,
                                AuditService auditService, BankMetrics metrics, MoneyRules rules) {
        this.rules = rules;
        this.auditService = auditService;
        this.metrics = metrics;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    /** Cash in: the owner (e.g. at an ATM) or an admin (a teller) can deposit. */
    @Transactional
    public MoneyMovementResult deposit(AuthenticatedUser caller, Long accountId, AmountRequest request,
                                       String idempotencyKey) {
        rules.validateKey(idempotencyKey);
        rules.requirePositive(request.amount());
        String fingerprint = MoneyRules.fingerprint(TransactionType.DEPOSIT, accountId, request.amount(),
                request.description());

        Account account = rules.lockCustomerAccount(accountId);
        if (!caller.isAdmin() && !account.isOwnedBy(caller.id())) {
            throw new ResourceNotFoundException("Account", accountId);
        }
        Optional<MoneyMovementResult> replay = replay(idempotencyKey, caller, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        rules.validateAmount(request.amount(), account.getCurrency());
        rules.requireActive(account);

        // The cash account isn't locked: nothing reads its balance to make a decision,
        // and locking it would make every deposit in that currency wait in line.
        Account cash = cashAccount(account.getCurrency());
        return post(TransactionType.DEPOSIT, request.amount(), account.getCurrency(), idempotencyKey,
                request.description(), caller, fingerprint, cash, account);
    }

    /** Cash out: only the owner can withdraw. */
    @Transactional
    public MoneyMovementResult withdraw(AuthenticatedUser caller, Long accountId, AmountRequest request,
                                        String idempotencyKey) {
        rules.validateKey(idempotencyKey);
        rules.requirePositive(request.amount());
        rules.requireVerifiedEmail(caller);
        String fingerprint = MoneyRules.fingerprint(TransactionType.WITHDRAWAL, accountId, request.amount(),
                request.description());

        Account account = rules.lockCustomerAccount(accountId);
        rules.requireOwner(caller, account);
        Optional<MoneyMovementResult> replay = replay(idempotencyKey, caller, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        rules.validateAmount(request.amount(), account.getCurrency());
        rules.requireActive(account);
        rules.requireFunds(account, request.amount());
        rules.requireWithinDailyLimit(account, request.amount());

        Account cash = cashAccount(account.getCurrency());
        return post(TransactionType.WITHDRAWAL, request.amount(), account.getCurrency(), idempotencyKey,
                request.description(), caller, fingerprint, account, cash);
    }

    @Transactional
    public MoneyMovementResult transfer(AuthenticatedUser caller, TransferRequest request, String idempotencyKey) {
        rules.validateKey(idempotencyKey);
        rules.requirePositive(request.amount());
        rules.requireVerifiedEmail(caller);
        if (!AccountNumberGenerator.isValid(request.toAccountNumber())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "toAccountNumber is not a valid account number (check digit mismatch)");
        }
        Long fromId = request.fromAccountId();
        Long toId = accountRepository.findCustomerAccountIdByNumber(request.toAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.toAccountNumber()));
        if (fromId.equals(toId)) {
            throw new BusinessRuleException(ErrorCode.SAME_ACCOUNT_TRANSFER, "Cannot transfer to the same account");
        }
        String fingerprint = MoneyRules.fingerprint(TransactionType.TRANSFER, fromId + ">" + toId, request.amount(),
                request.description());

        // Deadlock prevention: if transfer A->B locked A then B while B->A locked B
        // then A, each would wait on the other forever. Locking in ascending ID order
        // means every transaction asks for the same locks in the same sequence.
        Account first = rules.lockCustomerAccount(Math.min(fromId, toId));
        Account second = rules.lockCustomerAccount(Math.max(fromId, toId));
        Account from = fromId < toId ? first : second;
        Account to = fromId < toId ? second : first;

        rules.requireOwner(caller, from);
        Optional<MoneyMovementResult> replay = replay(idempotencyKey, caller, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        rules.validateAmount(request.amount(), from.getCurrency());
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessRuleException(ErrorCode.CURRENCY_MISMATCH, "Cannot transfer " + from.getCurrency()
                    + " to an account held in " + to.getCurrency());
        }
        rules.requireActive(from);
        rules.requireActive(to);
        rules.requireFunds(from, request.amount());
        rules.requireWithinDailyLimit(from, request.amount());

        return post(TransactionType.TRANSFER, request.amount(), from.getCurrency(), idempotencyKey,
                request.description(), caller, fingerprint, from, to);
    }

    // ---------------------------------------------------------------- posting

    /**
     * Writes the transaction and its two ledger entries. One DEBIT and one CREDIT of
     * the same amount keeps the books balanced by construction.
     */
    private MoneyMovementResult post(TransactionType type, BigDecimal amount, String currency, String idempotencyKey,
                                     String description, AuthenticatedUser caller, String fingerprint,
                                     Account debitAccount, Account creditAccount) {
        Transaction tx;
        try {
            tx = transactionRepository.saveAndFlush(new Transaction(type, amount, currency, idempotencyKey,
                    StringUtils.hasText(description) ? description.trim() : null,
                    userRepository.getReferenceById(caller.id()), fingerprint));
        } catch (DataIntegrityViolationException e) {
            // Same key used at the same moment for a request on other accounts (so the
            // row locks didn't make them wait for each other). The UNIQUE constraint
            // stops the second one.
            throw new BusinessRuleException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                    "Another request with this Idempotency-Key was processed concurrently");
        }
        List<LedgerEntry> entries = ledgerEntryRepository.saveAll(List.of(
                new LedgerEntry(tx, debitAccount, EntryDirection.DEBIT, amount),
                new LedgerEntry(tx, creditAccount, EntryDirection.CREDIT, amount)));
        TransactionResponse response = TransactionResponse.from(tx, entries);
        // Same DB transaction as the posting: the audit row exists if and only if the money moved.
        auditService.success(AuditAction.valueOf(type.name()), caller.id(), "TRANSACTION", tx.getId(),
                "amount=" + amount.toPlainString() + " " + currency
                        + " from=" + response.fromAccountNumber() + " to=" + response.toAccountNumber());
        metrics.moneyMovementCompleted(type, amount, currency);
        return new MoneyMovementResult(response, false);
    }

    // ---------------------------------------------------------------- idempotency

    private Optional<MoneyMovementResult> replay(String idempotencyKey, AuthenticatedUser caller, String fingerprint) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey).map(tx -> {
            if (!tx.getInitiatedBy().getId().equals(caller.id())) {
                throw new BusinessRuleException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                        "This Idempotency-Key has already been used");
            }
            if (!tx.getRequestFingerprint().equals(fingerprint)) {
                throw new BusinessRuleException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "This Idempotency-Key was already used for a different request");
            }
            List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionIdOrderByIdAsc(tx.getId());
            metrics.moneyMovementReplayed(tx.getType());
            return new MoneyMovementResult(TransactionResponse.from(tx, entries), true);
        });
    }

    private Account cashAccount(String currency) {
        return accountRepository.findByTypeAndCurrency(AccountType.CASH, currency)
                .orElseThrow(() -> new IllegalStateException("No cash account for " + currency));
    }
}
