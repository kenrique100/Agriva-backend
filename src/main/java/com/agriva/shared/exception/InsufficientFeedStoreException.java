package com.agriva.shared.exception;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class InsufficientFeedStoreException extends RuntimeException {

    private final BigDecimal requested;
    private final BigDecimal available;
    private final String unit;

    public InsufficientFeedStoreException(BigDecimal requested, BigDecimal available, String unit) {
        super(String.format("Insufficient feed in store: requested %s %s, available %s %s",
                requested != null ? requested.toPlainString() : "0", unit,
                available != null ? available.toPlainString() : "0", unit));
        this.requested = requested;
        this.available = available;
        this.unit = unit;
    }

}