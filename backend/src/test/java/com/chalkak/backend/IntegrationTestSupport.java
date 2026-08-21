package com.chalkak.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개별 테스트 클래스에 Context 설정을 추가하면 Spring이 컨텍스트를 새로 띄우므로 여기서 통일한다.
 *
 * <p>커밋 이후 동작({@code AFTER_COMMIT} 이벤트, {@code REQUIRES_NEW}, {@code @Async}, 실제 HTTP 호출)을 검증하는 테스트는 롤백으로 격리할 수 없어
 * 이 클래스를 상속하지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {
}
