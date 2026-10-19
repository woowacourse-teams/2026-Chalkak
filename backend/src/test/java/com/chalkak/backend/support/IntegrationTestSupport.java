package com.chalkak.backend.support;

import com.chalkak.backend.aireview.service.AiReviewGenerator;
import com.chalkak.backend.notification.service.NotificationSender;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 통합 테스트 공통 Context 설정. 격리(@Transactional 등)는 각 테스트가 선언한다. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    @MockitoBean
    protected NotificationSender notificationSender;

    /** 테스트가 실제 OpenAI를 호출하지 않도록 모든 통합 테스트에서 대체한다. */
    @MockitoBean
    protected AiReviewGenerator aiReviewGenerator;
}
