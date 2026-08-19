package com.chalkak.backend.exception;

public class NotFoundException extends BaseException {

    protected NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
