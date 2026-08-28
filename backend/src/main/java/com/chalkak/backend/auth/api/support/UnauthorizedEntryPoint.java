package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

@RequiredArgsConstructor
public class UnauthorizedEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationErrorResponder responder;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        responder.respond(
                response,
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED,
                "유효하지 않은 인증 정보입니다.");
    }
}
