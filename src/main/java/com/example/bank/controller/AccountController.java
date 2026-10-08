package com.example.bank.controller;

import com.example.bank.dto.AccountResponse;
import com.example.bank.dto.OpenAccountRequest;
import com.example.bank.security.AuthenticatedUser;
import com.example.bank.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse open(@AuthenticationPrincipal AuthenticatedUser caller,
                                @Valid @RequestBody OpenAccountRequest request) {
        return accountService.open(caller, request);
    }

    /** The caller's own accounts. Admins see all accounts via /api/admin/accounts. */
    @GetMapping
    public List<AccountResponse> listOwn(@AuthenticationPrincipal AuthenticatedUser caller) {
        return accountService.listOwn(caller);
    }

    @GetMapping("/{id}")
    public AccountResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return accountService.get(caller, id);
    }
}
