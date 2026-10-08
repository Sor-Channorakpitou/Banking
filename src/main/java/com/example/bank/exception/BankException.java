package com.example.bank.exception;

/**
 * Base class for every expected, business-level failure. Services throw these and
 * {@link GlobalExceptionHandler} turns them into JSON errors, so controllers never
 * need try/catch blocks.
 */
public class BankException extends RuntimeException {

    private final ErrorCode errorCode;

    public BankException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
