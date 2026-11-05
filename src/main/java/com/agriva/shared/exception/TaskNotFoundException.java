package com.agriva.shared.exception;

public class TaskNotFoundException extends ResourceNotFoundException {
    public TaskNotFoundException(String message) { super(message); }
}