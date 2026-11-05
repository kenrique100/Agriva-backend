package com.agriva.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PersonnelAccessDeniedException extends ResponseStatusException {
    public PersonnelAccessDeniedException(String reason) {
        super(HttpStatus.FORBIDDEN, reason);
    }
}