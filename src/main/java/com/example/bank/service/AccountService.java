package com.example.bank.service;

import com.example.bank.config.AccountProperties;
import com.example.bank.domain.Account;
import com.example.bank.domain.AuditAction;
import com.example.bank.domain.User;
import com.example.bank.dto.AccountResponse;
import com.example.bank.dto.AccountTransactionResponse;
import com.example.bank.dto.DailyLimitResponse;
import com.example.bank.dto.OpenAccountRequest;
import com.example.bank.dto.PageResponse;
import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.exception.ResourceNotFoundException;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.LedgerEntryRepository;
import com.example.bank.repository.UserRepository;
import com.example.bank.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountProperties accountProperties;
    private final AuditService auditService;
    private final MoneyRules moneyRules;

    public AccountService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository,
                          UserRepository userRepository, AccountNumberGenerator accountNumberGenerator,
                          AccountProperties accountProperties, AuditService auditService, MoneyRules moneyRules) {
        this.moneyRules = moneyRules;
        this.auditService = auditService;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userRepository = userRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.accountProperties = accountProperties;
    }

    @Transactional
    public AccountResponse open(AuthenticatedUser caller, OpenAccountRequest request) {
        String currency = request.currency().toUpperCase(Locale.ROOT);
        if (!accountProperties.supportedCurrencies().contains(currency)) {
            throw new BusinessRuleException(ErrorCode.UNSUPPORTED_CURRENCY,
                    "Currency " + currency + " is not supported. Supported: "
                            + accountProperties.supportedCurrencies());
        }
        // getReferenceById returns a proxy without a SELECT; we only need the foreign key.
        User owner = userRepository.getReferenceById(caller.id());
        Account account = accountRepository.save(new Account(accountNumberGenerator.next(), owner, currency));
        auditService.success(AuditAction.ACCOUNT_OPENED, caller.id(), "ACCOUNT", account.getId(),
                "number=" + account.getAccountNumber() + " currency=" + currency);
        return AccountResponse.from(account, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listOwn(AuthenticatedUser caller) {
        return withBalances(accountRepository.findByOwnerIdOrderByIdAsc(caller.id()));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(AuthenticatedUser caller, Long accountId) {
        Account account = findAccessible(caller, accountId);
        return AccountResponse.from(account, ledgerEntryRepository.balanceOf(account.getId()));
    }

    /** How much more can leave this account today. */
    @Transactional(readOnly = true)
    public DailyLimitResponse dailyLimit(AuthenticatedUser caller, Long accountId) {
        Account account = findAccessible(caller, accountId);
        MoneyRules.DailyLimit limit = moneyRules.dailyLimit(account);
        return new DailyLimitResponse(account.getCurrency(), limit.limit(), limit.usedToday(), limit.remaining());
    }

    /** Owner or admin; newest first. */
    @Transactional(readOnly = true)
    public PageResponse<AccountTransactionResponse> history(AuthenticatedUser caller, Long accountId,
                                                            int page, int size) {
        Account account = findAccessible(caller, accountId);
        return PageResponse.from(ledgerEntryRepository.findHistory(account.getId(), PageRequest.of(page, size))
                .map(entry -> AccountTransactionResponse.from(entry, account.getCurrency())));
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> listAll(int page, int size) {
        Page<Account> accounts = accountRepository.findAllByOrderByIdAsc(PageRequest.of(page, size));
        List<AccountResponse> content = withBalances(accounts.getContent());
        return new PageResponse<>(content, accounts.getNumber(), accounts.getSize(),
                accounts.getTotalElements(), accounts.getTotalPages());
    }

    // Status changes lock the row first (SELECT ... FOR UPDATE). For close() this is
    // essential: without the lock a deposit could land between "balance is zero" and
    // "mark closed", leaving money stuck in a closed account.

    @Transactional
    public AccountResponse freeze(AuthenticatedUser admin, Long accountId) {
        Account account = lock(accountId);
        account.freeze();
        auditService.success(AuditAction.ACCOUNT_FROZEN, admin.id(), "ACCOUNT", accountId, null);
        return AccountResponse.from(account, ledgerEntryRepository.balanceOf(accountId));
    }

    @Transactional
    public AccountResponse unfreeze(AuthenticatedUser admin, Long accountId) {
        Account account = lock(accountId);
        account.unfreeze();
        auditService.success(AuditAction.ACCOUNT_UNFROZEN, admin.id(), "ACCOUNT", accountId, null);
        return AccountResponse.from(account, ledgerEntryRepository.balanceOf(accountId));
    }

    @Transactional
    public AccountResponse close(AuthenticatedUser admin, Long accountId) {
        Account account = lock(accountId);
        BigDecimal balance = ledgerEntryRepository.balanceOf(accountId);
        if (balance.signum() != 0) {
            throw new BusinessRuleException(ErrorCode.ACCOUNT_STATE_CONFLICT,
                    "Account " + account.getAccountNumber() + " must have a zero balance to be closed (balance: "
                            + balance.stripTrailingZeros().toPlainString() + ")");
        }
        account.close();
        auditService.success(AuditAction.ACCOUNT_CLOSED, admin.id(), "ACCOUNT", accountId, null);
        return AccountResponse.from(account, balance);
    }

    /**
     * Owner or admin only. Anyone else gets "not found" rather than "forbidden", so
     * they can't learn which account IDs exist by probing.
     */
    Account findAccessible(AuthenticatedUser caller, Long accountId) {
        return accountRepository.findById(accountId)
                .filter(a -> caller.isAdmin() || a.isOwnedBy(caller.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    private Account lock(Long accountId) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (!account.isCustomerAccount()) {
            throw new BusinessRuleException(ErrorCode.ACCOUNT_STATE_CONFLICT,
                    "Internal bank accounts can't be frozen or closed");
        }
        return account;
    }

    private List<AccountResponse> withBalances(List<Account> accounts) {
        if (accounts.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> balances = ledgerEntryRepository
                .balancesOf(accounts.stream().map(Account::getId).toList()).stream()
                .collect(Collectors.toMap(LedgerEntryRepository.AccountBalance::getAccountId,
                        LedgerEntryRepository.AccountBalance::getBalance));
        return accounts.stream()
                .map(a -> AccountResponse.from(a, balances.getOrDefault(a.getId(), BigDecimal.ZERO)))
                .toList();
    }
}
