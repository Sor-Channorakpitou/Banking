package com.example.bank.controller;

import com.example.bank.dto.DecodeQrRequest;
import com.example.bank.dto.PayeeRequest;
import com.example.bank.dto.PayeeResponse;
import com.example.bank.dto.PaymentQrResponse;
import com.example.bank.dto.RecipientResponse;
import com.example.bank.security.AuthenticatedUser;
import com.example.bank.service.PayeeService;
import com.example.bank.service.PaymentQrService;
import com.example.bank.service.RecipientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Everything about "who am I paying": recipient check, saved payees, payment QR codes. */
@RestController
public class PayeeController {

    private final RecipientService recipientService;
    private final PayeeService payeeService;
    private final PaymentQrService paymentQrService;

    public PayeeController(RecipientService recipientService, PayeeService payeeService,
                           PaymentQrService paymentQrService) {
        this.recipientService = recipientService;
        this.payeeService = payeeService;
        this.paymentQrService = paymentQrService;
    }

    /** "Whose account is 1000 0000 033?" -> DARA C., USD. */
    @GetMapping("/api/accounts/lookup")
    public RecipientResponse lookup(@RequestParam @Pattern(regexp = "\\d{11}") String number) {
        return recipientService.lookup(number);
    }

    @GetMapping("/api/payees")
    public List<PayeeResponse> payees(@AuthenticationPrincipal AuthenticatedUser caller) {
        return payeeService.list(caller);
    }

    @PostMapping("/api/payees")
    @ResponseStatus(HttpStatus.CREATED)
    public PayeeResponse addPayee(@AuthenticationPrincipal AuthenticatedUser caller,
                                  @Valid @RequestBody PayeeRequest request) {
        return payeeService.add(caller, request);
    }

    @DeleteMapping("/api/payees/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePayee(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        payeeService.remove(caller, id);
    }

    /** "My QR": a code others scan to pay into this account, optionally with a fixed amount. */
    @GetMapping("/api/accounts/{id}/payment-qr")
    public PaymentQrResponse paymentQr(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id,
                                       @RequestParam(required = false) @Positive BigDecimal amount) {
        return paymentQrService.forAccount(caller, id, amount);
    }

    /** Reads a scanned code. Paying it is a normal POST /api/transfers. */
    @PostMapping("/api/payment-qr/decode")
    public PaymentQrResponse decode(@Valid @RequestBody DecodeQrRequest request) {
        return paymentQrService.decode(request.payload());
    }
}
