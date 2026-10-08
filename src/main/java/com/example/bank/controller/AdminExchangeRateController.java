package com.example.bank.controller;

import com.example.bank.dto.ExchangeRateResponse;
import com.example.bank.dto.SetExchangeRateRequest;
import com.example.bank.security.AuthenticatedUser;
import com.example.bank.service.ExchangeRateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/exchange-rates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public AdminExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    /** Publishes a new rate for a pair; it applies to every exchange from now on. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeRateResponse publish(@AuthenticationPrincipal AuthenticatedUser admin,
                                        @Valid @RequestBody SetExchangeRateRequest request) {
        return exchangeRateService.publish(admin, request);
    }
}
