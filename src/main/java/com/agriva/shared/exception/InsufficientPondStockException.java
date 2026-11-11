package com.agriva.shared.exception;

import lombok.Getter;

/**
 * Thrown when a mortality record would drive a pond's stock for a species
 * negative. Maps to HTTP 422 in GlobalExceptionHandler.
 */
@Getter
public class InsufficientPondStockException extends RuntimeException {

    private final String species;
    private final long requested;
    private final long available;

    public InsufficientPondStockException(String species, long requested, long available) {
        super(String.format(
                "Insufficient pond stock for species '%s': requested %d, available %d",
                species, requested, available));
        this.species = species;
        this.requested = requested;
        this.available = available;
    }

}