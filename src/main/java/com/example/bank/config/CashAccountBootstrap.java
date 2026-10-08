package com.example.bank.config;

import com.example.bank.domain.Account;
import com.example.bank.domain.AccountType;
import com.example.bank.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Makes sure the bank has a CASH account for every supported currency. It runs at
 * startup, so adding a currency to the config is enough to start using it.
 */
@Component
public class CashAccountBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CashAccountBootstrap.class);

    private final AccountProperties accountProperties;
    private final AccountRepository accountRepository;

    public CashAccountBootstrap(AccountProperties accountProperties, AccountRepository accountRepository) {
        this.accountProperties = accountProperties;
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String currency : accountProperties.supportedCurrencies()) {
            if (accountRepository.findByTypeAndCurrency(AccountType.CASH, currency).isPresent()) {
                continue;
            }
            try {
                accountRepository.save(Account.cash(currency));
                log.info("Created cash account for {}", currency);
            } catch (DataIntegrityViolationException e) {
                // Another instance starting at the same moment created it first; that's fine.
                log.debug("Cash account for {} already created concurrently", currency);
            }
        }
    }
}
