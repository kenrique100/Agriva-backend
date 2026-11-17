package com.agriva.shared.exception;

import lombok.Getter;

/**
 * Thrown when a mortality record would drive a poultry batch's headcount
 * negative. Maps to HTTP 422 in GlobalExceptionHandler.
 */
@Getter
public class InsufficientPoultryStockException extends RuntimeException {

    private final String batchCode;
    private final long requested;
    private final long available;

    public InsufficientPoultryStockException(String batchCode, long requested, long available) {
        super(String.format(
                "Insufficient poultry stock in batch '%s': requested %d, available %d",
                batchCode, requested, available));
        this.batchCode = batchCode;
        this.requested = requested;
        this.available = available;
    }
}