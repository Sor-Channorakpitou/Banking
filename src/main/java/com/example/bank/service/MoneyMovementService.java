package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.AccountType;
import com.example.bank.domain.AuditAction;
import com.example.bank.domain.EntryDirection;
import com.example.bank.domain.LedgerEntry;
import com.example.bank.domain.Money;
import com.example.bank.domain.Transaction;
import com.example.bank.domain.TransactionType;
import com.example.bank.dto.AmountRequest;
import com.example.bank.dto.TransactionResponse;
import com.example.bank.dto.TransferRequest;
import com.example.bank.exception.BankException;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    /** NUMERIC(19,4) leaves 15 digits before the decimal point. */
    private static final int MAX_INTEGER_DIGITS = 15;

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public MoneyMovementService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository,
                                TransactionRepository transactionRepository, UserRepository userRepository,
                                AuditService auditService) {
        this.auditService = auditService;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    /** Cash in: the owner (e.g. at an ATM) or an admin (a teller) can deposit. */
    @Transactional
    public MoneyMovementResult deposit(AuthenticatedUser caller, Long accountId, AmountRequest request,
                                       String idempotencyKey) {
        validateKey(idempotencyKey);
        requirePositive(request.amount());
        String fingerprint = fingerprint(TransactionType.DEPOSIT, accountId, request.amount(), request.description());

        Account account = lockCustomerAccount(accountId);
        if (!caller.isAdmin() && !account.isOwnedBy(caller.id())) {
            throw new ResourceNotFoundException("Account", accountId);
        }
        Optional<MoneyMovementResult> replay = replay(idempotencyKey, caller, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        validateAmount(request.amount(), account.getCurrency());
        requireActive(account);

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
        validateKey(idempotencyKey);
        requirePositive(request.amount());
        String fingerprint = fingerprint(TransactionType.WITHDRAWAL, accountId, request.amount(),
                request.description());

        Account account = lockCustomerAccount(accountId);
        requireOwner(caller, account);
        Optional<MoneyMovementResult> replay = replay(idempotencyKey, caller, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        validateAmount(request.amount(), account.getCurrency());
        requireActive(account);
        requireFunds(account, request.amount());

        Account cash = cashAccount(account.getCurrency());
        return post(TransactionType.WITHDRAWAL, request.amount(), account.getCurrency(), idempotencyKey,
                request.description(), caller, fingerprint, account, cash);
    }

    @Transactional
    public MoneyMovementResult transfer(AuthenticatedUser caller, TransferRequest request, String idempotencyKey) {
        validateKey(idempotencyKey);
        requirePositive(request.amount());
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
        String fingerprint = fingerprint(TransactionType.TRANSFER, fromId + ">" + toId, request.amount(),
                request.description());

        // Deadlock prevention: if transfer A->B locked A then B while B->A locked B
        // then A, each would wait on the other forever. Locking in ascending ID order
        // means every transaction asks for the same locks in the same sequence.
        Account first = lockCustomerAccount(Math.min(fromId, toId));
        Account second = lockCustomerAccount(Math.max(fromId, toId));
        Account from = fromId < toId ? first : second;
        Account to = fromId < toId ? second : first;

        requireOwner(caller, from);
        Optional<MoneyMovementResult> replay = replay(idempotencyKey, caller, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        validateAmount(request.amount(), from.getCurrency());
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessRuleException(ErrorCode.CURRENCY_MISMATCH, "Cannot transfer " + from.getCurrency()
                    + " to an account held in " + to.getCurrency());
        }
        requireActive(from);
        requireActive(to);
        requireFunds(from, request.amount());

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
            return new MoneyMovementResult(TransactionResponse.from(tx, entries), true);
        });
    }

    /** SHA-256 over the request fields, e.g. "DEPOSIT|42|100.5|rent". */
    static String fingerprint(Object... parts) {
        String canonical = Arrays.stream(parts)
                .map(p -> switch (p) {
                    case null -> "";
                    case BigDecimal d -> d.stripTrailingZeros().toPlainString(); // 100.50 == 100.5
                    default -> p.toString().trim();
                })
                .collect(Collectors.joining("|"));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }

    // ---------------------------------------------------------------- rules

    private static void validateKey(String key) {
        if (!StringUtils.hasText(key) || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key header must be 1-" + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new BusinessRuleException(ErrorCode.INVALID_AMOUNT, "Amount must be greater than zero");
        }
    }

    private static void validateAmount(BigDecimal amount, String currency) {
        if (!Money.hasValidScale(amount, currency)) {
            throw new BusinessRuleException(ErrorCode.INVALID_AMOUNT, currency + " amounts can have at most "
                    + Money.fractionDigits(currency) + " decimal places");
        }
        if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw new BusinessRuleException(ErrorCode.INVALID_AMOUNT, "Amount is too large");
        }
    }

    private static void requireActive(Account account) {
        if (!account.isActive()) {
            throw new BusinessRuleException(ErrorCode.ACCOUNT_NOT_ACTIVE,
                    "Account " + account.getAccountNumber() + " is " + account.getStatus());
        }
    }

    /** Runs only while the account is locked, so the balance can't change before we post. */
    private void requireFunds(Account account, BigDecimal amount) {
        BigDecimal balance = ledgerEntryRepository.balanceOf(account.getId());
        if (balance.compareTo(amount) < 0) {
            throw new BusinessRuleException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds in account "
                    + account.getAccountNumber() + ": balance " + Money.forDisplay(balance, account.getCurrency())
                    + ", requested " + amount.toPlainString());
        }
    }

    private static void requireOwner(AuthenticatedUser caller, Account account) {
        if (account.isOwnedBy(caller.id())) {
            return;
        }
        if (caller.isAdmin()) {
            throw new BankException(ErrorCode.ACCESS_DENIED, "Only the account owner can move money out of it");
        }
        throw new ResourceNotFoundException("Account", account.getId());
    }

    private Account lockCustomerAccount(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .filter(Account::isCustomerAccount)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    private Account cashAccount(String currency) {
        return accountRepository.findByTypeAndCurrency(AccountType.CASH, currency)
                .orElseThrow(() -> new IllegalStateException("No cash account for " + currency));
    }
}
