package com.chalkak.backend.analytics.service;

/**
 * 한 KST 날짜의 접근 로그에서 센 지표.
 *
 * <p>
 * {@code visitors}(userId가 남은 요청을 보낸 회원 수), {@code topicViewers}(주제 조회에 성공한 회원
 * 수), {@code postCreations}(게시물 생성에 성공한 요청 수)는 제외 회원을 뺀다. {@code requests},
 * {@code server5xx}, {@code latencyAvgMs}, {@code latencyP95Ms}는 서버 상태를 보는 값이라
 * 제외 회원을 빼지 않는다. {@code postCreations}는 DB의 {@code posts}와 맞춰 보는 교차 검증용이다.
 */
public record AccessLogMetrics(
        long visitors,
        long topicViewers,
        long requests,
        long server5xx,
        long latencyAvgMs,
        long latencyP95Ms,
        long postCreations) {
}
