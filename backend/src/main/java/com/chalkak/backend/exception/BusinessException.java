package com.chalkak.backend.exception;

public class BusinessException extends BaseException {

    protected BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
