package com.example.bank.exception;

public class EmailAlreadyUsedException extends BankException {

    public EmailAlreadyUsedException(String email) {
        super(ErrorCode.EMAIL_ALREADY_USED, "Email is already registered: " + email);
    }
}
