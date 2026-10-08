package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.Money;
import com.example.bank.dto.PaymentQrResponse;
import com.example.bank.dto.RecipientResponse;
import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.exception.ResourceNotFoundException;
import com.example.bank.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * "My QR" for receiving money, and reading a scanned code before paying it.
 * Decoding only reads the code: the payment itself is a normal transfer
 * (POST /api/transfers), so every transfer rule and the idempotency key apply.
 */
@Service
public class PaymentQrService {

    private final AccountService accountService;
    private final RecipientService recipientService;

    public PaymentQrService(AccountService accountService, RecipientService recipientService) {
        this.accountService = accountService;
        this.recipientService = recipientService;
    }

    @Transactional(readOnly = true)
    public PaymentQrResponse forAccount(AuthenticatedUser caller, Long accountId, BigDecimal amount) {
        Account account = accountService.findAccessible(caller, accountId);
        if (!account.isOwnedBy(caller.id())) {
            throw new ResourceNotFoundException("Account", accountId);
        }
        if (amount != null && (amount.signum() <= 0 || !Money.hasValidScale(amount, account.getCurrency()))) {
            throw new BusinessRuleException(ErrorCode.INVALID_AMOUNT, "Invalid amount for " + account.getCurrency());
        }
        String name = RecipientService.maskName(account.getOwner().getFullName());
        String payload = PaymentQrCodec.encode(new PaymentQrCodec.Payment(account.getAccountNumber(),
                account.getCurrency(), amount, name));
        return new PaymentQrResponse(payload, account.getAccountNumber(), account.getCurrency(), amount, name);
    }

    /** Checks the code and that its account can receive money right now. */
    @Transactional(readOnly = true)
    public PaymentQrResponse decode(String payload) {
        PaymentQrCodec.Payment payment = PaymentQrCodec.decode(payload);
        RecipientResponse recipient = recipientService.lookup(payment.accountNumber());
        if (!recipient.currency().equals(payment.currency())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "QR currency doesn't match the account");
        }
        return new PaymentQrResponse(payload, recipient.accountNumber(), recipient.currency(), payment.amount(),
                recipient.holderName());
    }
}
