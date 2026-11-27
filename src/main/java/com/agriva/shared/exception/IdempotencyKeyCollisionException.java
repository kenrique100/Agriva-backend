package com.agriva.shared.exception;

import lombok.Getter;

@Getter
public class IdempotencyKeyCollisionException extends RuntimeException {

    private final String key;

    public IdempotencyKeyCollisionException(String key) {
        super("Idempotency-Key already used by a concurrent request: " + key);
        this.key = key;
    }

}