package com.example.bank.repository;

import com.example.bank.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * The balance is never stored; it's always this sum. CREDIT adds to the
     * balance and DEBIT subtracts from it.
     */
    @Query("""
            select coalesce(sum(case when e.direction = com.example.bank.domain.EntryDirection.CREDIT
                                     then e.amount else -e.amount end), 0)
            from LedgerEntry e
            where e.account.id = :accountId
            """)
    BigDecimal balanceOf(Long accountId);

    /** Balances of several accounts in one query, instead of one query per account. */
    @Query("""
            select e.account.id as accountId,
                   sum(case when e.direction = com.example.bank.domain.EntryDirection.CREDIT
                            then e.amount else -e.amount end) as balance
            from LedgerEntry e
            where e.account.id in :accountIds
            group by e.account.id
            """)
    List<AccountBalance> balancesOf(Collection<Long> accountIds);

    /** Spring Data projection: one row of {@link #balancesOf}. */
    interface AccountBalance {
        Long getAccountId();

        BigDecimal getBalance();
    }
}
