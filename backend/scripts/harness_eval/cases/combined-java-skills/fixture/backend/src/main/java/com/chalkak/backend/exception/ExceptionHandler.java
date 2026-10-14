package com.chalkak.backend.exception;

public class ExceptionHandler {
  public record ErrorResponse(String errorCode, String message) {}

  public ErrorResponse toResponse(BusinessException exception) {
    return new ErrorResponse(exception.getErrorCode().name(), exception.getMessage());
  }
}
