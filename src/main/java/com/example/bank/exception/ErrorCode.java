package com.example.bank.exception;

import org.springframework.http.HttpStatus;

/**
 * Every error the API can return, with its HTTP status and a short title. The
 * {@code code} (the enum name) appears in each error body so clients can branch on
 * a stable value instead of parsing human-readable messages.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    UNSUPPORTED_CURRENCY(HttpStatus.BAD_REQUEST, "Unsupported currency"),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid refresh token"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "Email already registered"),
    ACCOUNT_STATE_CONFLICT(HttpStatus.CONFLICT, "Invalid account state"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "Idempotency key conflict"),

    // 422: the request is well-formed but breaks a business rule.
    INVALID_AMOUNT(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid amount"),
    ACCOUNT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Account not active"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds"),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Currency mismatch"),
    SAME_ACCOUNT_TRANSFER(HttpStatus.UNPROCESSABLE_ENTITY, "Same account transfer"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused"),

    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
