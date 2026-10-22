package com.agriva.exception;

public class DuplicateExpenseException extends RuntimeException {
    public DuplicateExpenseException(String message) {
        super(message);
    }
}