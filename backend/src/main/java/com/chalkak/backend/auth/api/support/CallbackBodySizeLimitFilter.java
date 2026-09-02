package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 이미지 처리 콜백 본문 크기를 인증 전에 제한한다.
 *
 * <p>{@code @RequestBody}는 컨트롤러 메서드가 실행되기 전에 채워지므로 본문 전체가 HMAC 검증보다 먼저
 * 메모리에 올라간다. 서명이 없는 요청도 큰 본문을 반복해 보내는 것만으로 힙과 대역폭을 소모할 수 있다.
 *
 * <p>정상 콜백은 메타데이터를 포함해도 수 KB 수준이다. Lambda가 metaAttributes를 8KB로 자르므로 기본
 * 상한은 그보다 넉넉히 잡되, 인증까지 가지 못할 요청은 여기서 끊는다.
 */
public class CallbackBodySizeLimitFilter extends OncePerRequestFilter {

    private static final String TOO_LARGE_BODY = """
            {"errorCode":"%s","message":"이미지 처리 콜백 본문이 너무 큽니다."}"""
            .formatted(ErrorCode.BUSINESS_ERROR);

    private final long maxBytes;

    public CallbackBodySizeLimitFilter(
            @Value("${chalkak.callback.max-body-bytes}") long maxBytes
    ) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("chalkak.callback.max-body-bytes는 0보다 커야 합니다.");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            rejectTooLarge(response);
            return;
        }
        try {
            filterChain.doFilter(new BodySizeLimitRequest(request, maxBytes), response);
        } catch (BodyTooLargeException exception) {
            // Content-Length가 없거나 실제 전송량이 그보다 큰 경우다. 읽는 도중에야 드러난다.
            rejectTooLarge(response);
        }
    }

    private void rejectTooLarge(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.reset();
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(TOO_LARGE_BODY);
    }

    /** 읽은 바이트가 상한을 넘으면 더 읽지 않고 끊는다. */
    private static final class BodySizeLimitRequest extends HttpServletRequestWrapper {

        private final long maxBytes;

        private BodySizeLimitRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new BodySizeLimitInputStream(super.getInputStream(), maxBytes);
        }
    }

    private static final class BodySizeLimitInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long readBytes;

        private BodySizeLimitInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                countRead(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                countRead(count);
            }
            return count;
        }

        private void countRead(int count) {
            readBytes += count;
            if (readBytes > maxBytes) {
                throw new BodyTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }

    /**
     * 스트림 계약상 검사 지점에서 체크 예외를 던질 수 없어 런타임 예외로 올린다. 필터가 곧바로 받아
     * 413으로 바꾸므로 밖으로 새어 나가지 않는다.
     */
    private static final class BodyTooLargeException extends RuntimeException {

        private BodyTooLargeException() {
            super(null, null, false, false);
        }
    }
}
