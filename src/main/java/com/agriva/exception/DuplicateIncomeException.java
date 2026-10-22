package com.agriva.exception;

public class DuplicateIncomeException extends RuntimeException {
    public DuplicateIncomeException(String message) {
        super(message);
    }
}