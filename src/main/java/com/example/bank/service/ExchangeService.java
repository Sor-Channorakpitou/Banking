package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.AccountType;
import com.example.bank.domain.AuditAction;
import com.example.bank.domain.EntryDirection;
import com.example.bank.domain.LedgerEntry;
import com.example.bank.domain.Transaction;
import com.example.bank.domain.TransactionType;
import com.example.bank.dto.ExchangeRequest;
import com.example.bank.dto.ExchangeResponse;
import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.LedgerEntryRepository;
import com.example.bank.repository.TransactionRepository;
import com.example.bank.repository.UserRepository;
import com.example.bank.security.AuthenticatedUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Exchange between two of the caller's own accounts in different currencies.
 * Same steps as every money movement (lock in ID order, idempotency after the
 * locks, check rules, post), but with four ledger entries instead of two, so that
 * each currency balances on its own:
 * <pre>
 *   DEBIT  customer USD   100.00     CREDIT FX-USD        100.00
 *   DEBIT  FX-KHR     409,000        CREDIT customer KHR 409,000
 * </pre>
 * Debits and credits are never mixed across currencies.
 */
@Service
public class ExchangeService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    private final AuditService auditService;
    private final BankMetrics metrics;
    private final MoneyRules rules;

    public ExchangeService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository,
                           TransactionRepository transactionRepository, UserRepository userRepository,
                           ExchangeRateService exchangeRateService, AuditService auditService, BankMetrics metrics,
                           MoneyRules rules) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.exchangeRateService = exchangeRateService;
        this.auditService = auditService;
        this.metrics = metrics;
        this.rules = rules;
    }

    public record Result(ExchangeResponse exchange, boolean replayed) {
    }

    @Transactional
    public Result exchange(AuthenticatedUser caller, ExchangeRequest request, String idempotencyKey) {
        rules.validateKey(idempotencyKey);
        rules.requirePositive(request.amount());
        rules.requireVerifiedEmail(caller);
        Long fromId = request.fromAccountId();
        Long toId = request.toAccountId();
        if (fromId.equals(toId)) {
            throw new BusinessRuleException(ErrorCode.SAME_ACCOUNT_TRANSFER, "Choose two different accounts");
        }
        String fingerprint = MoneyRules.fingerprint(TransactionType.EXCHANGE, fromId + ">" + toId, request.amount());

        Account first = rules.lockCustomerAccount(Math.min(fromId, toId));
        Account second = rules.lockCustomerAccount(Math.max(fromId, toId));
        Account from = fromId < toId ? first : second;
        Account to = fromId < toId ? second : first;
        rules.requireOwner(caller, from);
        rules.requireOwner(caller, to); // exchange is between your own accounts only

        Optional<Result> replay = replay(idempotencyKey, caller, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        rules.validateAmount(request.amount(), from.getCurrency());
        rules.requireActive(from);
        rules.requireActive(to);
        ExchangeRateService.Quote quote = exchangeRateService.convert(from.getCurrency(), to.getCurrency(),
                request.amount());
        if (quote.convertedAmount().signum() <= 0) {
            throw new BusinessRuleException(ErrorCode.INVALID_AMOUNT,
                    "Amount is too small to exchange into " + to.getCurrency());
        }
        rules.validateAmount(quote.convertedAmount(), to.getCurrency());
        rules.requireFunds(from, request.amount());
        rules.requireWithinDailyLimit(from, request.amount());

        Transaction tx;
        try {
            tx = transactionRepository.saveAndFlush(Transaction.exchange(request.amount(), from.getCurrency(),
                    quote.convertedAmount(), to.getCurrency(), quote.rate(), quote.rateBase(), idempotencyKey,
                    userRepository.getReferenceById(caller.id()), fingerprint));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                    "Another request with this Idempotency-Key was processed concurrently");
        }
        Account fxFrom = fxAccount(from.getCurrency());
        Account fxTo = fxAccount(to.getCurrency());
        List<LedgerEntry> entries = ledgerEntryRepository.saveAll(List.of(
                new LedgerEntry(tx, from, EntryDirection.DEBIT, request.amount()),
                new LedgerEntry(tx, fxFrom, EntryDirection.CREDIT, request.amount()),
                new LedgerEntry(tx, fxTo, EntryDirection.DEBIT, quote.convertedAmount()),
                new LedgerEntry(tx, to, EntryDirection.CREDIT, quote.convertedAmount())));

        ExchangeResponse response = ExchangeResponse.from(tx, entries);
        auditService.success(AuditAction.EXCHANGE, caller.id(), "TRANSACTION", tx.getId(),
                "sold=" + request.amount().toPlainString() + " " + from.getCurrency()
                        + " bought=" + quote.convertedAmount().toPlainString() + " " + to.getCurrency()
                        + " rate=" + quote.rate().toPlainString());
        metrics.moneyMovementCompleted(TransactionType.EXCHANGE, request.amount(), from.getCurrency());
        return new Result(response, false);
    }

    private Optional<Result> replay(String idempotencyKey, AuthenticatedUser caller, String fingerprint) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey).map(tx -> {
            if (!tx.getInitiatedBy().getId().equals(caller.id())) {
                throw new BusinessRuleException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                        "This Idempotency-Key has already been used");
            }
            if (!tx.getRequestFingerprint().equals(fingerprint)) {
                throw new BusinessRuleException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "This Idempotency-Key was already used for a different request");
            }
            metrics.moneyMovementReplayed(TransactionType.EXCHANGE);
            return new Result(ExchangeResponse.from(tx,
                    ledgerEntryRepository.findByTransactionIdOrderByIdAsc(tx.getId())), true);
        });
    }

    private Account fxAccount(String currency) {
        return accountRepository.findByTypeAndCurrency(AccountType.FX, currency)
                .orElseThrow(() -> new IllegalStateException("No FX account for " + currency));
    }
}
