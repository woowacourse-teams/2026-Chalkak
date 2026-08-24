package com.chalkak.backend.exception;

import org.springframework.http.HttpStatus;

public class DomainErrorHttpMapper {

    public HttpStatus statusOf(BaseException exception) {
        if (exception instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (exception instanceof UnauthorizedException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (exception instanceof BusinessException) {
            return HttpStatus.BAD_REQUEST;
        }
        throw new IllegalArgumentException("지원하지 않는 예외 타입입니다.");
    }
}
