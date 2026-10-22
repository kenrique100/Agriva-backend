package com.agriva.exception;

public class PersonnelNotFoundException extends ResourceNotFoundException {
    public PersonnelNotFoundException(String message) { super(message); }
}