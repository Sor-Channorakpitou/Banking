package com.example.bank.config;

import com.example.bank.domain.Account;
import com.example.bank.domain.AccountType;
import com.example.bank.domain.ExchangeRate;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Startup setup the bank's own bookkeeping needs. For every supported currency it
 * creates a CASH account (counterpart of deposits/withdrawals) and an FX account
 * (counterpart of exchanges), and it publishes the configured starting rates for
 * pairs that have none yet. Adding a currency to the config is enough to start
 * using it.
 */
@Component
public class InternalAccountsBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InternalAccountsBootstrap.class);

    private final AccountProperties accountProperties;
    private final ExchangeProperties exchangeProperties;
    private final AccountRepository accountRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public InternalAccountsBootstrap(AccountProperties accountProperties, ExchangeProperties exchangeProperties,
                                     AccountRepository accountRepository,
                                     ExchangeRateRepository exchangeRateRepository) {
        this.accountProperties = accountProperties;
        this.exchangeProperties = exchangeProperties;
        this.accountRepository = accountRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String currency : accountProperties.supportedCurrencies()) {
            for (AccountType type : List.of(AccountType.CASH, AccountType.FX)) {
                ensureAccount(type, currency);
            }
        }
        for (ExchangeProperties.InitialRate rate : exchangeProperties.initialRates()) {
            String base = rate.base().toUpperCase(Locale.ROOT);
            String quote = rate.quote().toUpperCase(Locale.ROOT);
            if (exchangeRateRepository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByIdDesc(base, quote).isEmpty()) {
                exchangeRateRepository.save(new ExchangeRate(base, quote, rate.buyRate(), rate.sellRate(), null));
                log.info("Published starting rate {}/{}: buy {} sell {}", base, quote, rate.buyRate(), rate.sellRate());
            }
        }
    }

    private void ensureAccount(AccountType type, String currency) {
        if (accountRepository.findByTypeAndCurrency(type, currency).isPresent()) {
            return;
        }
        try {
            accountRepository.save(Account.internal(type, currency));
            log.info("Created {} account for {}", type, currency);
        } catch (DataIntegrityViolationException e) {
            // Another instance starting at the same moment created it first; that's fine.
            log.debug("{} account for {} already created concurrently", type, currency);
        }
    }
}
