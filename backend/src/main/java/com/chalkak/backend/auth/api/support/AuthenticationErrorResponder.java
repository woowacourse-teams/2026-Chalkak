package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Security 필터에서 끊긴 요청의 응답 본문을 만든다. 필터는 컨트롤러보다 앞이라
 * {@code GlobalExceptionHandler}가 잡지 못하므로, 그대로 두면 본문 없는 응답이 나가
 * 클라이언트가 인증 실패와 다른 오류를 같은 방식으로 파싱할 수 없다.
 */
@RequiredArgsConstructor
public class AuthenticationErrorResponder {

    private final ObjectMapper objectMapper;

    public void respond(
            HttpServletResponse response,
            HttpStatus status,
            ErrorCode errorCode,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                new ErrorResponse(errorCode.name(), message));
    }
}
