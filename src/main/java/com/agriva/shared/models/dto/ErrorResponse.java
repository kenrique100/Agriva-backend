package com.agriva.shared.models.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String message,
        String path,
        String requestId,
        Map<String, String> errors
) {
    public ErrorResponse(LocalDateTime timestamp, int status, String message, String path, String requestId) {
        this(timestamp, status, message, path, requestId, null);
    }
}