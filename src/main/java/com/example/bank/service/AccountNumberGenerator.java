package com.example.bank.service;

import com.example.bank.repository.AccountRepository;
import org.springframework.stereotype.Component;

/**
 * Account number = 10-digit database sequence value + 1 Luhn check digit (the same
 * scheme as card numbers). The check digit catches any single mistyped digit and
 * most swapped neighbors.
 */
@Component
public class AccountNumberGenerator {

    private final AccountRepository accountRepository;

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String next() {
        String base = Long.toString(accountRepository.nextAccountNumber());
        return base + luhnCheckDigit(base);
    }

    static int luhnCheckDigit(String digits) {
        int sum = 0;
        boolean doubleIt = true; // the digit next to the check digit is doubled
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }

    public static boolean isValid(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("\\d{2,}")) {
            return false;
        }
        String base = accountNumber.substring(0, accountNumber.length() - 1);
        int check = accountNumber.charAt(accountNumber.length() - 1) - '0';
        return luhnCheckDigit(base) == check;
    }
}
