package com.agriva.exception;

public class CropNotFoundException extends ResourceNotFoundException {
    public CropNotFoundException(String message) {
        super(message);
    }
}