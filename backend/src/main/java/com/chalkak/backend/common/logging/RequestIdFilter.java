package com.chalkak.backend.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 장애가 났을 때 요청 하나를 로그로 추적하기 위해 요청마다 requestId를 부여하고 접근 로그를 한 줄 남긴다.
 *
 * <p>
 * Security 필터보다 먼저 실행해야 인증 단계에서 거절된 401·403 요청도 같은 requestId로 기록된다. 응답 헤더는 응답이
 * 커밋되기 전에 넣어야 하므로 체인을 호출하기 전에 설정한다.
 *
 * <p>
 * 접근 로그의 route는 매칭된 경로 템플릿만 쓴다. 원본 URI에는 식별자와 쿼리 값이 섞여 있어 남기지 않는다.
 *
 * <p>
 * 이 필터는 인증보다 먼저 실행되어 접근 로그를 쓸 때는 SecurityContext가 이미 비어 있다. 그래서 체인 안쪽에서 요청 속성에
 * 담아 둔 회원 식별자가 있을 때만 userId로 남긴다.
 *
 * <p>
 * 피드 조회의 topicDate는 주제 날짜별 조회 수를 집계하기 위해 남긴다. 상태가 200이면 Spring이 ISO 날짜로 검증을 마친
 * 값이므로 파라미터 원문을 그대로 쓰고, 그 밖의 경로·메서드·상태에서는 검증되지 않은 입력이므로 남기지 않는다. 정렬·페이지 조건은
 * Controller에서 검증과 응답 변환을 마친 요청 속성만 사용한다.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("chalkak.access");
    private static final String ACTUATOR_PATH = "/actuator";
    private static final String ACTUATOR_PATH_PREFIX = ACTUATOR_PATH + "/";
    private static final String FEED_METHOD = "GET";
    private static final String FEED_ROUTE = "/api/v1/posts";
    private static final String TOPIC_DATE_PARAMETER = "topicDate";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        // 체인 밖으로 예외가 새면 컨테이너가 500으로 응답하므로 정상 종료할 때만 실제 상태로 바꾼다.
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

        try {
            String requestId = UUID.randomUUID().toString();
            MDC.put(LogFields.REQUEST_ID, requestId);
            response.setHeader(LogFields.REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            logAccess(
                    request,
                    status,
                    startNanos
            );
            MDC.remove(LogFields.REQUEST_ID);
        }
    }

    private void logAccess(
            HttpServletRequest request,
            int status,
            long startNanos
    ) {
        if (isActuatorPath(request.getRequestURI())) {
            return;
        }

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        String route = resolveRoute(request);
        LoggingEventBuilder event = ACCESS_LOG.atInfo()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_ACCESS)
                .addKeyValue(LogFields.METHOD, request.getMethod())
                .addKeyValue(LogFields.ROUTE, route)
                .addKeyValue(LogFields.STATUS, status)
                .addKeyValue(LogFields.DURATION_MS, durationMs);
        if (request.getAttribute(LogFields.USER_ID_REQUEST_ATTRIBUTE) instanceof String userId) {
            event = event.addKeyValue(LogFields.USER_ID, userId);
        }
        if (isSuccessfulFeedQuery(
                request,
                route,
                status
        )) {
            event = event.addKeyValue(
                    LogFields.TOPIC_DATE,
                    request.getParameter(TOPIC_DATE_PARAMETER));
            if (request.getAttribute(
                    LogFields.FEED_QUERY_REQUEST_ATTRIBUTE) instanceof FeedQueryLog feedQuery) {
                event = event.addKeyValue(LogFields.SORT, feedQuery.sort())
                        .addKeyValue(LogFields.PAGE, feedQuery.page())
                        .addKeyValue(LogFields.PAGE_SIZE, feedQuery.pageSize());
            }
        }
        event.log("access");
    }

    private boolean isActuatorPath(String requestUri) {
        return requestUri.equals(ACTUATOR_PATH) || requestUri.startsWith(ACTUATOR_PATH_PREFIX);
    }

    private boolean isSuccessfulFeedQuery(
            HttpServletRequest request,
            String route,
            int status
    ) {
        return FEED_METHOD.equals(request.getMethod())
                && FEED_ROUTE.equals(route)
                && status == HttpServletResponse.SC_OK;
    }

    /** DispatcherServlet에 닿기 전에 거절된 요청은 매칭 결과가 없으므로 고정 값으로 묶는다. */
    private String resolveRoute(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String route) {
            return route;
        }

        return LogFields.UNMATCHED_ROUTE;
    }
}
