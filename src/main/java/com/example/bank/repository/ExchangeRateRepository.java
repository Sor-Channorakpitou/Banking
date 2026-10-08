package com.example.bank.repository;

import com.example.bank.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /** The current rate for a pair: its newest row. */
    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByIdDesc(String baseCurrency,
                                                                                 String quoteCurrency);

    /** The current rate of every pair. */
    @Query("""
            select r from ExchangeRate r
            where r.id in (select max(r2.id) from ExchangeRate r2 group by r2.baseCurrency, r2.quoteCurrency)
            order by r.baseCurrency, r.quoteCurrency
            """)
    List<ExchangeRate> findCurrent();
}
