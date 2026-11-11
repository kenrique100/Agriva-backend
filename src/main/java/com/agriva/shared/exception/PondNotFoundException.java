package com.agriva.shared.exception;

public class PondNotFoundException extends ResourceNotFoundException {
    public PondNotFoundException(String message) {
        super(message);
    }
}