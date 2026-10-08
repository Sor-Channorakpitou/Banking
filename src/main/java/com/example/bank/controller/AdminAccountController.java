package com.example.bank.controller;

import com.example.bank.dto.AccountResponse;
import com.example.bank.dto.PageResponse;
import com.example.bank.service.AccountService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Status changes are POST "actions" (/freeze, /close) rather than PATCH status=...
 * Each action is a business operation with its own rules, and this shape keeps
 * those rules in the service instead of in a generic field update.
 */
@RestController
@RequestMapping("/api/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final AccountService accountService;

    public AdminAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public PageResponse<AccountResponse> listAll(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return accountService.listAll(page, size);
    }

    @PostMapping("/{id}/freeze")
    public AccountResponse freeze(@PathVariable Long id) {
        return accountService.freeze(id);
    }

    @PostMapping("/{id}/unfreeze")
    public AccountResponse unfreeze(@PathVariable Long id) {
        return accountService.unfreeze(id);
    }

    @PostMapping("/{id}/close")
    public AccountResponse close(@PathVariable Long id) {
        return accountService.close(id);
    }
}
