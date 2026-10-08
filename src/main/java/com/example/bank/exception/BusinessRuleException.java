package com.example.bank.exception;

/**
 * A request that is valid in form but breaks a business rule: insufficient
 * funds, frozen account, currency mismatch, and so on. The {@link ErrorCode}
 * says which rule was broken.
 */
public class BusinessRuleException extends BankException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
