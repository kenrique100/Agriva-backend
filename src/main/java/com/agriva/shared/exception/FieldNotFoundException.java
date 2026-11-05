package com.agriva.shared.exception;

public class FieldNotFoundException extends ResourceNotFoundException {
    public FieldNotFoundException(String message) {
        super(message);
    }
}