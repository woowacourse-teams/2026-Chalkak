package com.chalkak.backend.exception;

public class UnauthorizedException extends BaseException {

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
