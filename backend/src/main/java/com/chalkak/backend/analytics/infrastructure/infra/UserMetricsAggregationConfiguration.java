package com.chalkak.backend.analytics.infrastructure.infra;

import com.chalkak.backend.analytics.service.UserMetricsAggregationRunner;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 집계 작업과 전용 스레드는 함께 있어야 동작하므로 같은 조건 아래에서 함께 등록한다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "chalkak.analytics.aggregation", name = "enabled", havingValue = "true")
public class UserMetricsAggregationConfiguration {

    // 수 분 걸리는 집계가 런타임 스냅샷 같은 공용 예약 작업을 밀어내지 않게 전용 스레드에서 실행한다.
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler analyticsAggregationScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("analytics-aggregation-");
        return scheduler;
    }

    @Bean
    public UserMetricsAggregationScheduler userMetricsAggregationScheduler(
            UserMetricsAggregationRunner aggregationRunner,
            Clock clock
    ) {
        return new UserMetricsAggregationScheduler(aggregationRunner, clock);
    }
}
