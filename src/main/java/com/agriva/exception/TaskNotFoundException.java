package com.agriva.exception;

public class TaskNotFoundException extends ResourceNotFoundException {
    public TaskNotFoundException(String message) { super(message); }
}