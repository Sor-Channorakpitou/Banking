package com.example.bank.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of the account-security endpoints. */
public final class SecurityRequests {

    private SecurityRequests() {
    }

    public record VerifyEmail(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    public record ForgotPassword(@NotBlank @Email String email) {
    }

    public record ResetPassword(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "\\d{6}") String code,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record ChangePassword(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record TotpCode(@NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    /** The secret to type into an authenticator app, and the same as a scannable link. */
    public record TotpSetup(String secret, String otpauthUri) {
    }
}
