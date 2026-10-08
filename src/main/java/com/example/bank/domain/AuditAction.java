package com.example.bank.domain;

public enum AuditAction {
    USER_REGISTERED,
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    ACCOUNT_OPENED,
    ACCOUNT_FROZEN,
    ACCOUNT_UNFROZEN,
    ACCOUNT_CLOSED,
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER
}
