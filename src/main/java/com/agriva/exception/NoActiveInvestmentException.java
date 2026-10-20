package com.agriva.exception;

public class NoActiveInvestmentException extends RuntimeException {
    public NoActiveInvestmentException(String message) {
        super(message);
    }
}