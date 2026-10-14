package com.chalkak.backend.analytics.service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 접근 로그 집계 조회. 지표에서 제외할 회원은 구현체가 걸러낸다. 조회에 실패하면 예외를 던진다.
 */
public interface AccessLogMetricsReader {

    /**
     * KST 하루({@code metricDate} 0시부터 다음 날 0시 전까지)의 접근 로그에서 지표를 센다. 빈 값은 관측 시작일 이전이라
     * 접근 로그 지표가 없다는 뜻이다.
     */
    Optional<AccessLogMetrics> read(LocalDate metricDate);
}
