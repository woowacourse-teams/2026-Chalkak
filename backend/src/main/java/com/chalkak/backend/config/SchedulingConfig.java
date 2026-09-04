package com.chalkak.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션 전체의 주기 작업을 켠다.
 *
 * <p>{@code @EnableScheduling}은 컨텍스트 전체에 걸리므로, 이 설정이 살아 있으면 모든
 * {@code @SpringBootTest}가 스케줄러까지 띄운다. 테스트가 자기 시계를 고정해 둔 사이 실제 시각으로
 * 도는 작업이 같은 테스트 DB의 행을 건드리면 원인을 찾기 어려운 실패가 된다. 그래서 플래그로 끌 수
 * 있게 두고 test 프로필에서는 끈다. 작업 빈 자체는 남으므로 테스트는 메서드를 직접 부른다.
 *
 * <p>이 플래그는 특정 작업이 아니라 스케줄링 기능 자체를 끄므로 어느 작업에도 매이지 않은 중립적인
 * 이름을 쓴다. 작업 이름이 붙은 키를 여기 걸면 나중에 주기 작업이 하나 더 늘었을 때, 그 작업과
 * 아무 관계 없는 키가 새 작업까지 조용히 끄게 된다. 개별 작업은 각자의 키로 끈다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "chalkak.scheduling",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchedulingConfig {
}
