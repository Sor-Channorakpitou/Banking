package com.example.bank.service;

import com.example.bank.domain.AuditAction;
import com.example.bank.domain.Payee;
import com.example.bank.dto.PayeeRequest;
import com.example.bank.dto.PayeeResponse;
import com.example.bank.dto.RecipientResponse;
import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.exception.ResourceNotFoundException;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.PayeeRepository;
import com.example.bank.security.AuthenticatedUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PayeeService {

    private final PayeeRepository payeeRepository;
    private final AccountRepository accountRepository;
    private final RecipientService recipientService;
    private final AuditService auditService;

    public PayeeService(PayeeRepository payeeRepository, AccountRepository accountRepository,
                        RecipientService recipientService, AuditService auditService) {
        this.payeeRepository = payeeRepository;
        this.accountRepository = accountRepository;
        this.recipientService = recipientService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PayeeResponse> list(AuthenticatedUser caller) {
        return payeeRepository.findByOwnerIdOrderByNicknameAsc(caller.id()).stream()
                .map(p -> toResponse(p, recipientService.find(p.getAccountNumber())))
                .toList();
    }

    /** Only accounts that can receive money right now can be saved, and never your own. */
    @Transactional
    public PayeeResponse add(AuthenticatedUser caller, PayeeRequest request) {
        RecipientResponse recipient = recipientService.lookup(request.accountNumber());
        boolean own = accountRepository.findByAccountNumber(request.accountNumber())
                .map(a -> a.isOwnedBy(caller.id()))
                .orElse(false);
        if (own) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "This is your own account");
        }
        if (payeeRepository.existsByOwnerIdAndAccountNumber(caller.id(), request.accountNumber())) {
            throw new BusinessRuleException(ErrorCode.DATA_CONFLICT, "This account is already in your payees");
        }
        Payee payee;
        try {
            payee = payeeRepository.saveAndFlush(new Payee(caller.id(), request.nickname().trim(),
                    request.accountNumber()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException(ErrorCode.DATA_CONFLICT, "This account is already in your payees");
        }
        auditService.success(AuditAction.PAYEE_ADDED, caller.id(), "PAYEE", payee.getId(),
                "account=" + payee.getAccountNumber());
        return toResponse(payee, Optional.of(recipient));
    }

    @Transactional
    public void remove(AuthenticatedUser caller, Long payeeId) {
        Payee payee = payeeRepository.findByIdAndOwnerId(payeeId, caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("Payee", payeeId));
        payeeRepository.delete(payee);
        auditService.success(AuditAction.PAYEE_REMOVED, caller.id(), "PAYEE", payeeId,
                "account=" + payee.getAccountNumber());
    }

    private static PayeeResponse toResponse(Payee p, Optional<RecipientResponse> recipient) {
        return new PayeeResponse(p.getId(), p.getNickname(), p.getAccountNumber(),
                recipient.map(RecipientResponse::currency).orElse(null),
                recipient.map(RecipientResponse::holderName).orElse(null),
                recipient.isPresent());
    }
}
