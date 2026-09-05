package com.chalkak.backend.exception;

public record ErrorResponse(
        String errorCode,
        String message
) {
}
