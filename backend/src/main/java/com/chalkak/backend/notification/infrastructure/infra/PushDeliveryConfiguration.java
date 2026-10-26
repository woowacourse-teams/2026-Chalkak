package com.chalkak.backend.notification.infrastructure.infra;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PushDeliveryConfiguration {
    // 첫 Lease를 얻을 때 스레드가 시작된다. 공용 스케줄러와 SQS 자동 연장 실행기를 대체하지 않는다.
    @Bean(defaultCandidate = false, destroyMethod = "shutdown")
    public ScheduledExecutorService notificationLeaseRenewalExecutor() {
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("notification-lease-", 0).factory());
    }
}
