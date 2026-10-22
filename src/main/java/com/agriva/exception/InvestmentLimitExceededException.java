package com.agriva.exception;

public class InvestmentLimitExceededException extends RuntimeException {
    public InvestmentLimitExceededException(String message) {
        super(message);
    }
}