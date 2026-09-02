package com.chalkak.backend.exception;

public class ForbiddenException extends BaseException {

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
