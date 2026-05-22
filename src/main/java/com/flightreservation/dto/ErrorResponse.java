package com.flightreservation.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        List<FieldError> errors
) {

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now(), List.of());
    }
}
