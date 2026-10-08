package com.example.bank.controller;

import com.example.bank.dto.SecurityRequests;
import com.example.bank.security.AuthenticatedUser;
import com.example.bank.service.AccountSecurityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountSecurityController {

    private final AccountSecurityService service;

    public AccountSecurityController(AccountSecurityService service) {
        this.service = service;
    }

    // Public: the person may not be signed in (just registered, or forgot the password).

    @PostMapping("/api/auth/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody SecurityRequests.VerifyEmail request) {
        service.verifyEmail(request);
    }

    @PostMapping("/api/auth/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody SecurityRequests.ForgotPassword request) {
        service.forgotPassword(request);
    }

    @PostMapping("/api/auth/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody SecurityRequests.ResetPassword request) {
        service.resetPassword(request);
    }

    // Signed in.

    @PostMapping("/api/users/me/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@AuthenticationPrincipal AuthenticatedUser caller) {
        service.resendVerification(caller);
    }

    @PostMapping("/api/users/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser caller,
                               @Valid @RequestBody SecurityRequests.ChangePassword request) {
        service.changePassword(caller, request);
    }

    @PostMapping("/api/users/me/two-step/setup")
    public SecurityRequests.TotpSetup startTwoStep(@AuthenticationPrincipal AuthenticatedUser caller) {
        return service.startTotpSetup(caller);
    }

    @PostMapping("/api/users/me/two-step/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enableTwoStep(@AuthenticationPrincipal AuthenticatedUser caller,
                              @Valid @RequestBody SecurityRequests.TotpCode request) {
        service.enableTotp(caller, request.code());
    }

    @PostMapping("/api/users/me/two-step/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableTwoStep(@AuthenticationPrincipal AuthenticatedUser caller,
                               @Valid @RequestBody SecurityRequests.TotpCode request) {
        service.disableTotp(caller, request.code());
    }
}
