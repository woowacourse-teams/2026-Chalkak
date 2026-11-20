package com.chalkak.backend.admin.service.jev;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.jev.domain.JevMetrics;
import com.chalkak.backend.jev.repository.JevMetricsRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminJevMetricsService {

    private final JevMetricsRepository repository;

    public JevMetrics get(
            Instant from,
            Instant to,
            Boolean preview
    ) {
        if (from == null || to == null || !from.isBefore(to)
                || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "조회 기간은 0초 초과 31일 이하여야 합니다.");
        }
        var rows = repository.findSamples(
                from,
                to,
                preview,
                10001
        );
        boolean truncated = rows.size() > 10000;
        var samples = rows.stream().limit(10000).toList();
        return JevMetrics.calculate(
                samples,
                repository
                        .findCalls(samples.stream().map(JevMetricsRepository.Sample::id).toList()),
                truncated
        );
    }
}
