package com.chalkak.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 테스트 상위 클래스.
 *
 * <p>Context 설정과 프로파일을 여기서 통일한다. 개별 테스트 클래스에 Context 설정을 추가하면 Spring이 컨텍스트를 새로 띄우므로 추가하지 않는다.
 *
 * <p>트랜잭션 롤백으로 격리한다. 커밋 이후 동작({@code AFTER_COMMIT} 이벤트, {@code REQUIRES_NEW}, {@code @Async}, 실제 HTTP 호출)을 검증하는
 * 테스트는 이 클래스를 상속하지 않고 별도로 정리한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestSupport {
}
