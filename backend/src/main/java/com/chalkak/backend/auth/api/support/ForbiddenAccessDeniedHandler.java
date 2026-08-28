package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

@RequiredArgsConstructor
public class ForbiddenAccessDeniedHandler implements AccessDeniedHandler {

    private final AuthenticationErrorResponder responder;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        responder.respond(
                response,
                HttpStatus.FORBIDDEN,
                ErrorCode.UNAUTHORIZED,
                "접근 권한이 없습니다.");
    }
}
