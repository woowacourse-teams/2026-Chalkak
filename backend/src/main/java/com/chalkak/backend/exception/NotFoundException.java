package com.chalkak.backend.exception;

public class NotFoundException extends BaseException {

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
