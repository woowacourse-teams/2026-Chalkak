package com.chalkak.backend.exception;

import com.chalkak.backend.common.logging.LogFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final DomainErrorHttpMapper httpMapper = new DomainErrorHttpMapper();

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e
    ) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(this::fieldErrorMessage)
                .orElse("요청 값이 올바르지 않습니다.");

        return clientErrorResponse(
                e,
                HttpStatus.BAD_REQUEST,
                ErrorCode.BUSINESS_ERROR,
                message
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException e
    ) {
        String message = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> constraintMessage(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage()
                        )))
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");

        return clientErrorResponse(
                e,
                HttpStatus.BAD_REQUEST,
                ErrorCode.BUSINESS_ERROR,
                message
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e
    ) {
        return clientErrorResponse(
                e,
                HttpStatus.BAD_REQUEST,
                ErrorCode.BUSINESS_ERROR,
                "JSON 형식이 올바르지 않거나 요청 본문이 비어 있습니다."
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e
    ) {
        return clientErrorResponse(
                e,
                HttpStatus.BAD_REQUEST,
                ErrorCode.BUSINESS_ERROR,
                fieldMessage(e.getName(), "요청 값의 형식이 올바르지 않습니다.")
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e
    ) {
        return clientErrorResponse(
                e,
                HttpStatus.BAD_REQUEST,
                ErrorCode.BUSINESS_ERROR,
                fieldMessage(e.getParameterName(), "필수 요청 값이 없습니다.")
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(
            MissingRequestHeaderException e
    ) {
        return clientErrorResponse(
                e,
                HttpStatus.BAD_REQUEST,
                ErrorCode.BUSINESS_ERROR,
                fieldMessage(e.getHeaderName(), "필수 요청 값이 없습니다.")
        );
    }

    /**
     * 없는 경로는 접근 로그가 status 404로 이미 남기고, 스캐너 요청이 경고 로그를 채우지 않도록 여기서는 기록하지 않는다.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleApiNotFoundException(Exception e) {
        return response(HttpStatus.NOT_FOUND, ErrorCode.BUSINESS_ERROR, "요청한 API를 찾을 수 없습니다.");
    }

    @ExceptionHandler({BaseException.class})
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException e) {
        return response(e);
    }

    /**
     * 인가 거부는 Security의 처리기가 응답을 만든다. 아래 캐치올이 삼키면 403이 500으로 바뀐다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDeniedException(AccessDeniedException e)
            throws AccessDeniedException {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        HttpStatus status = statusOf(e);
        if (status.is4xxClientError()) {
            return clientErrorResponse(
                    e,
                    status,
                    ErrorCode.BUSINESS_ERROR,
                    "지원하지 않는 요청 방식이거나 형식입니다."
            );
        }

        log.atError()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_ERROR)
                .addKeyValue(LogFields.ERROR_CODE, ErrorCode.INTERNAL_ERROR.name())
                .addKeyValue(LogFields.STATUS, status.value())
                .setCause(e)
                .log("처리되지 않은 예외");

        return response(status, ErrorCode.INTERNAL_ERROR, "서버에서 요청을 처리하지 못했습니다.");
    }

    private HttpStatus statusOf(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springErrorResponse) {
            return HttpStatus.valueOf(springErrorResponse.getStatusCode().value());
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ResponseEntity<ErrorResponse> response(BaseException exception) {
        HttpStatus status = httpMapper.statusOf(exception);
        warnClientError(
                exception,
                status,
                exception.getErrorCode()
        );

        return ResponseEntity.status(status)
                .body(new ErrorResponse(exception.getErrorCode().name(), exception.getMessage()));
    }

    private ResponseEntity<ErrorResponse> clientErrorResponse(
            Exception e,
            HttpStatus status,
            ErrorCode errorCode,
            String message
    ) {
        warnClientError(
                e,
                status,
                errorCode
        );

        return response(
                status,
                errorCode,
                message
        );
    }

    /**
     * 요청 하나가 왜 실패했는지 requestId와 함께 남긴다. 예외 메시지에는 사용자가 보낸 값이 들어갈 수 있어 예외 종류만 기록하고,
     * 4xx는 클라이언트 오류이므로 스택 트레이스도 남기지 않는다.
     */
    private void warnClientError(
            Exception e,
            HttpStatus status,
            ErrorCode errorCode
    ) {
        log.atWarn()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_ERROR)
                .addKeyValue(LogFields.ERROR_CODE, errorCode.name())
                .addKeyValue(LogFields.STATUS, status.value())
                .addKeyValue(LogFields.EXCEPTION, e.getClass().getSimpleName())
                .log("요청 실패");
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, ErrorCode errorCode, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(errorCode.name(), message));
    }

    private String fieldErrorMessage(FieldError error) {
        if (error.isBindingFailure()) {
            return fieldMessage(error.getField(), "요청 값의 형식이 올바르지 않습니다.");
        }
        return constraintMessage(error.getField(), error.getDefaultMessage());
    }

    /**
     * 응답 {@code message}는 클라이언트가 사용자에게 그대로 보여주는 문구다. 제약에 작성된 문구가 있으면 필드명을 덧붙이지 않고
     * 그대로 전달하고, 처리기가 일반 문구로 대신할 때만 어느 값이 문제인지 알 수 있도록 필드명을 붙인다.
     */
    private String constraintMessage(String field, String message) {
        if (message == null) {
            return fieldMessage(field, "요청 값이 올바르지 않습니다.");
        }
        return message;
    }

    private String fieldMessage(String field, String message) {
        if (field == null || field.isBlank()) {
            return message;
        }
        return field + ": " + message;
    }
}
