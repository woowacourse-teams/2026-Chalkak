package com.chalkak.backend.exception;

import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final DomainErrorHttpMapper httpMapper = new DomainErrorHttpMapper();

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e
    ) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> fieldMessage(
                        error.getField(),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "요청 값이 올바르지 않습니다.")
                ))
                .orElse("요청 값이 올바르지 않습니다.");

        return response(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_ERROR, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException e
    ) {
        String message = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> fieldMessage(
                                result.getMethodParameter().getParameterName(),
                                Objects.requireNonNullElse(error.getDefaultMessage(), "요청 값이 올바르지 않습니다.")
                        )))
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");

        return response(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_ERROR, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BUSINESS_ERROR,
                "JSON 형식이 올바르지 않거나 요청 본문이 비어 있습니다."
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BUSINESS_ERROR,
                fieldMessage(e.getName(), "요청 값의 형식이 올바르지 않습니다.")
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BUSINESS_ERROR,
                fieldMessage(e.getParameterName(), "필수 요청 값이 없습니다.")
        );
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleApiNotFoundException(Exception e) {
        return response(HttpStatus.NOT_FOUND, ErrorCode.BUSINESS_ERROR, "요청한 API를 찾을 수 없습니다.");
    }

    @ExceptionHandler({BaseException.class})
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException e) {
        return response(e);
    }

    private ResponseEntity<ErrorResponse> response(BaseException exception) {
        return ResponseEntity.status(httpMapper.statusOf(exception))
                .body(new ErrorResponse(exception.getErrorCode().name(), exception.getMessage()));
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode errorCode, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(errorCode.name(), message));
    }

    private String fieldMessage(String field, String message) {
        if (field == null || field.isBlank()) {
            return message;
        }
        return field + ": " + message;
    }
}
