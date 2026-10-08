package com.example.bank.controller;

import com.example.bank.dto.AccountResponse;
import com.example.bank.dto.AccountTransactionResponse;
import com.example.bank.dto.OpenAccountRequest;
import com.example.bank.dto.PageResponse;
import com.example.bank.security.AuthenticatedUser;
import com.example.bank.dto.StatementResponse;
import com.example.bank.service.AccountService;
import com.example.bank.service.StatementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final StatementService statementService;

    public AccountController(AccountService accountService, StatementService statementService) {
        this.accountService = accountService;
        this.statementService = statementService;
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

    @GetMapping("/{id}/transactions")
    public PageResponse<AccountTransactionResponse> transactions(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return accountService.history(caller, id, page, size);
    }

    /** Monthly statement, e.g. GET /api/accounts/4/statements/2026-10 */
    @GetMapping("/{id}/statements/{month}")
    public StatementResponse statement(@AuthenticationPrincipal AuthenticatedUser caller,
                                       @PathVariable Long id,
                                       @PathVariable YearMonth month) {
        return statementService.monthly(caller, id, month);
    }
}
