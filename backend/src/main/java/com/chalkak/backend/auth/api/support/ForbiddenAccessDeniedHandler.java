package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

@RequiredArgsConstructor
public class ForbiddenAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ForbiddenAccessDeniedHandler.class);

    private final AuthenticationErrorResponder responder;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        log.atWarn()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_ERROR)
                .addKeyValue(LogFields.ERROR_CODE, ErrorCode.FORBIDDEN.name())
                .addKeyValue(LogFields.STATUS, HttpStatus.FORBIDDEN.value())
                .addKeyValue(LogFields.EXCEPTION, accessDeniedException.getClass().getSimpleName())
                .log("접근 거부");
        responder.respond(
                response,
                HttpStatus.FORBIDDEN,
                ErrorCode.FORBIDDEN,
                "접근 권한이 없습니다.");
    }
}
