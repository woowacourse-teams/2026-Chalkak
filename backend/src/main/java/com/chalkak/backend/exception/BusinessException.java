package com.chalkak.backend.exception;

public class BusinessException extends BaseException {

    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
