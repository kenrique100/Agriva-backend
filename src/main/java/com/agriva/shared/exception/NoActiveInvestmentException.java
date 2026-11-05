package com.agriva.shared.exception;

public class NoActiveInvestmentException extends RuntimeException {
    public NoActiveInvestmentException(String message) {
        super(message);
    }
}