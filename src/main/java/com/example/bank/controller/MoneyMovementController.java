package com.example.bank.controller;

import com.example.bank.dto.AmountRequest;
import com.example.bank.dto.TransactionResponse;
import com.example.bank.dto.TransferRequest;
import com.example.bank.security.AuthenticatedUser;
import com.example.bank.service.MoneyMovementResult;
import com.example.bank.service.MoneyMovementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every money endpoint requires an Idempotency-Key header (a client-generated
 * UUID). If the network drops after the server moved the money, the client can
 * safely resend the same request with the same key. It gets the original result
 * back (200 + "Idempotent-Replayed: true") instead of moving the money twice.
 */
@RestController
public class MoneyMovementController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final MoneyMovementService moneyMovementService;

    public MoneyMovementController(MoneyMovementService moneyMovementService) {
        this.moneyMovementService = moneyMovementService;
    }

    @PostMapping("/api/accounts/{id}/deposit")
    public ResponseEntity<TransactionResponse> deposit(@AuthenticationPrincipal AuthenticatedUser caller,
                                                       @PathVariable Long id,
                                                       @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                                       @Valid @RequestBody AmountRequest request) {
        return respond(moneyMovementService.deposit(caller, id, request, idempotencyKey));
    }

    @PostMapping("/api/accounts/{id}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@AuthenticationPrincipal AuthenticatedUser caller,
                                                        @PathVariable Long id,
                                                        @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                                        @Valid @RequestBody AmountRequest request) {
        return respond(moneyMovementService.withdraw(caller, id, request, idempotencyKey));
    }

    @PostMapping("/api/transfers")
    public ResponseEntity<TransactionResponse> transfer(@AuthenticationPrincipal AuthenticatedUser caller,
                                                        @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                                        @Valid @RequestBody TransferRequest request) {
        return respond(moneyMovementService.transfer(caller, request, idempotencyKey));
    }

    private static ResponseEntity<TransactionResponse> respond(MoneyMovementResult result) {
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.transaction());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.transaction());
    }
}
