package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.Money;
import com.example.bank.exception.BankException;
import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.exception.ResourceNotFoundException;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.LedgerEntryRepository;
import com.example.bank.security.AuthenticatedUser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * The checks every money operation shares (transfers, withdrawals, exchanges...),
 * kept in one place so each rule is written, and tested, only once.
 */
@Component
public class MoneyRules {

    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    /** NUMERIC(19,4) leaves 15 digits before the decimal point. */
    private static final int MAX_INTEGER_DIGITS = 15;

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public MoneyRules(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public void validateKey(String key) {
        if (!StringUtils.hasText(key) || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key header must be 1-" + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
    }

    public void requirePositive(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new BusinessRuleException(ErrorCode.INVALID_AMOUNT, "Amount must be greater than zero");
        }
    }

    /** Right number of decimals for the currency, and fits in the database column. */
    public void validateAmount(BigDecimal amount, String currency) {
        if (!Money.hasValidScale(amount, currency)) {
            throw new BusinessRuleException(ErrorCode.INVALID_AMOUNT, currency + " amounts can have at most "
                    + Money.fractionDigits(currency) + " decimal places");
        }
        if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw new BusinessRuleException(ErrorCode.INVALID_AMOUNT, "Amount is too large");
        }
    }

    public void requireActive(Account account) {
        if (!account.isActive()) {
            throw new BusinessRuleException(ErrorCode.ACCOUNT_NOT_ACTIVE,
                    "Account " + account.getAccountNumber() + " is " + account.getStatus());
        }
    }

    /** Call only while the account is locked, so the balance can't change before posting. */
    public void requireFunds(Account account, BigDecimal amount) {
        BigDecimal balance = ledgerEntryRepository.balanceOf(account.getId());
        if (balance.compareTo(amount) < 0) {
            throw new BusinessRuleException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds in account "
                    + account.getAccountNumber() + ": balance " + Money.forDisplay(balance, account.getCurrency())
                    + ", requested " + amount.toPlainString());
        }
    }

    /** Only the owner moves money out. Others get 404; an admin gets an explicit 403. */
    public void requireOwner(AuthenticatedUser caller, Account account) {
        if (account.isOwnedBy(caller.id())) {
            return;
        }
        if (caller.isAdmin()) {
            throw new BankException(ErrorCode.ACCESS_DENIED, "Only the account owner can move money out of it");
        }
        throw new ResourceNotFoundException("Account", account.getId());
    }

    /** SELECT ... FOR UPDATE on a customer account (internal accounts look like "not found"). */
    public Account lockCustomerAccount(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .filter(Account::isCustomerAccount)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    /** SHA-256 over the request fields, e.g. "DEPOSIT|42|100.5|rent". */
    public static String fingerprint(Object... parts) {
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
}
