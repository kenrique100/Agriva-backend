package com.agriva.exception;

public class InvalidTaskStateException extends BusinessException {
    public InvalidTaskStateException(String message) { super(message); }
}