package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.EntryDirection;
import com.example.bank.domain.LedgerEntry;
import com.example.bank.domain.Money;
import com.example.bank.dto.StatementResponse;
import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.repository.LedgerEntryRepository;
import com.example.bank.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Monthly statements, computed from the ledger on demand. Because the ledger is
 * append-only, a past month's statement is the same every time it's generated.
 * Months are calendar months in UTC; a bank serving one country would use its
 * local time zone instead.
 */
@Service
public class StatementService {

    private final AccountService accountService;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final Clock clock = Clock.systemUTC();

    public StatementService(AccountService accountService,
                            LedgerEntryRepository ledgerEntryRepository) {
        this.accountService = accountService;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * REPEATABLE_READ: the opening-balance query and the entries query must see the
     * same snapshot. Otherwise a transfer committing between them could make the
     * statement fail to add up.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StatementResponse monthly(AuthenticatedUser caller, Long accountId, YearMonth month) {
        if (month.isAfter(YearMonth.now(clock))) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Cannot produce a statement for a future month");
        }
        Account account = accountService.findAccessible(caller, accountId);
        String currency = account.getCurrency();
        Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal opening = ledgerEntryRepository.balanceBefore(accountId, start);
        BigDecimal running = opening;
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        List<StatementResponse.Line> lines = new ArrayList<>();
        for (LedgerEntry entry : ledgerEntryRepository.findForPeriod(accountId, start, end)) {
            if (entry.getDirection() == EntryDirection.CREDIT) {
                credits = credits.add(entry.getAmount());
                running = running.add(entry.getAmount());
            } else {
                debits = debits.add(entry.getAmount());
                running = running.subtract(entry.getAmount());
            }
            lines.add(new StatementResponse.Line(entry.getCreatedAt(), entry.getTransaction().getId(),
                    entry.getTransaction().getType(), entry.getTransaction().getDescription(),
                    entry.getDirection(), Money.forDisplay(entry.getAmount(), currency),
                    Money.forDisplay(running, currency)));
        }
        return new StatementResponse(account.getAccountNumber(), currency, month, start, end,
                Money.forDisplay(opening, currency), Money.forDisplay(credits, currency),
                Money.forDisplay(debits, currency), Money.forDisplay(running, currency), lines);
    }
}
