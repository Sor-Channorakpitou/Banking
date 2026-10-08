package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.dto.RecipientResponse;
import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.exception.ResourceNotFoundException;
import com.example.bank.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/** Answers "whose account is this?" before money is sent to it. */
@Service
public class RecipientService {

    private final AccountRepository accountRepository;

    public RecipientService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** Only active customer accounts are found: you can't send money to the others anyway. */
    @Transactional(readOnly = true)
    public RecipientResponse lookup(String accountNumber) {
        return find(accountNumber).orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));
    }

    @Transactional(readOnly = true)
    public Optional<RecipientResponse> find(String accountNumber) {
        if (!AccountNumberGenerator.isValid(accountNumber)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Not a valid account number (check digit mismatch)");
        }
        return accountRepository.findCustomerAccountWithOwner(accountNumber)
                .filter(Account::isActive)
                .map(a -> new RecipientResponse(a.getAccountNumber(), a.getCurrency(),
                        maskName(a.getOwner().getFullName())));
    }

    /** "Dara Chan Sok" -> "DARA C. S."; a single name keeps only its first letter: "D***". */
    static String maskName(String fullName) {
        String[] parts = fullName.trim().toUpperCase(Locale.ROOT).split("\\s+");
        if (parts.length == 1) {
            return parts[0].isEmpty() ? "***" : parts[0].charAt(0) + "***";
        }
        return parts[0] + " " + Arrays.stream(parts, 1, parts.length)
                .map(p -> p.charAt(0) + ".")
                .collect(Collectors.joining(" "));
    }
}
