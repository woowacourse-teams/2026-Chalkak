package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

@RequiredArgsConstructor
public class UnauthorizedEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(UnauthorizedEntryPoint.class);

    private final AuthenticationErrorResponder responder;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        // RFC 6750이 요구하는 헤더다. 필터가 막은 401과 컨트롤러가 던진 401은 상태 코드도
        // 본문도 같아서, 이 헤더가 둘을 구별하는 유일한 신호가 된다.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        // Bearer 검증 실패 메시지에는 토큰 내용이 섞일 수 있어 예외 종류만 남긴다.
        log.atWarn()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_ERROR)
                .addKeyValue(LogFields.ERROR_CODE, ErrorCode.UNAUTHORIZED.name())
                .addKeyValue(LogFields.STATUS, HttpStatus.UNAUTHORIZED.value())
                .addKeyValue(LogFields.EXCEPTION,
                        authenticationException.getClass().getSimpleName())
                .log("인증 실패");
        responder.respond(
                response,
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED,
                "유효하지 않은 인증 정보입니다.");
    }
}
