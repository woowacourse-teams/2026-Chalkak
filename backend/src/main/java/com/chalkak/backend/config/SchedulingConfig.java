package com.chalkak.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업을 켠다.
 *
 * <p>{@code @EnableScheduling}은 컨텍스트 전체에 걸리므로, 이 설정이 살아 있으면 모든
 * {@code @SpringBootTest}가 스케줄러까지 띄운다. 테스트가 자기 시계를 고정해 둔 사이 실제 시각으로
 * 도는 작업이 같은 테스트 DB의 행을 지우면 원인을 찾기 어려운 실패가 된다. 그래서 플래그로 끌 수
 * 있게 두고 test 프로필에서는 끈다. 스케줄러 빈 자체는 남으므로 테스트는 메서드를 직접 부른다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "chalkak.auth.refresh-token.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchedulingConfig {
}
