package com.example.bank.exception;

/**
 * Same message whether the email is unknown or the password is wrong, so the
 * login endpoint can't be used to find out which emails are registered.
 */
public class InvalidCredentialsException extends BankException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }
}
