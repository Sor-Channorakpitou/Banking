package com.example.bank.controller;

import com.example.bank.dto.ExchangeQuoteResponse;
import com.example.bank.dto.ExchangeRateResponse;
import com.example.bank.dto.ExchangeRequest;
import com.example.bank.dto.ExchangeResponse;
import com.example.bank.security.AuthenticatedUser;
import com.example.bank.service.ExchangeRateService;
import com.example.bank.service.ExchangeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class ExchangeController {

    private final ExchangeRateService exchangeRateService;
    private final ExchangeService exchangeService;

    public ExchangeController(ExchangeRateService exchangeRateService, ExchangeService exchangeService) {
        this.exchangeRateService = exchangeRateService;
        this.exchangeService = exchangeService;
    }

    /** Today's published rates, e.g. USD/KHR buy 4,090 sell 4,110. */
    @GetMapping("/api/exchange-rates")
    public List<ExchangeRateResponse> rates() {
        return exchangeRateService.current();
    }

    /** What exchanging this amount would give right now (nothing is booked). */
    @GetMapping("/api/exchange-rates/quote")
    public ExchangeQuoteResponse quote(@RequestParam @Pattern(regexp = "[A-Za-z]{3}") String from,
                                       @RequestParam @Pattern(regexp = "[A-Za-z]{3}") String to,
                                       @RequestParam @Positive BigDecimal amount) {
        return exchangeRateService.quote(from, to, amount);
    }

    /** Exchange between two of your own accounts. Requires an Idempotency-Key, like every money movement. */
    @PostMapping("/api/exchanges")
    public ResponseEntity<ExchangeResponse> exchange(@AuthenticationPrincipal AuthenticatedUser caller,
                                                     @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                     @Valid @RequestBody ExchangeRequest request) {
        ExchangeService.Result result = exchangeService.exchange(caller, request, idempotencyKey);
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result.exchange());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.exchange());
    }
}
