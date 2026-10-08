package com.example.bank.exception;

public class ResourceNotFoundException extends BankException {

    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resource + " not found: " + id);
    }
}
