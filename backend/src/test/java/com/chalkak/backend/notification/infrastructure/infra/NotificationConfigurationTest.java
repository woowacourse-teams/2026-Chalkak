package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.service.NotificationMessageFactory;
import com.chalkak.backend.notification.service.NotificationSender;
import com.chalkak.backend.notification.service.PostModerationPendingNotificationListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationConfiguration.class)
            .withBean(NotificationSender.class, () -> message -> true)
            .withPropertyValues(
                    "chalkak.admin.notification.admin-web-base-url=https://admin.example.com"
            );

    @Test
    @DisplayName("공통 알림 설정은 메시지 생성기와 이벤트 리스너를 등록한다")
    void create_deliveryEnabled_registersCommonNotificationBeans() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.admin.notification.delivery-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NotificationProperties.class);
                    assertThat(context).hasSingleBean(NotificationMessageFactory.class);
                    assertThat(context).hasSingleBean(NotificationSender.class);
                    assertThat(context)
                            .hasSingleBean(PostModerationPendingNotificationListener.class);
                    assertThat(context).doesNotHaveBean(SlackNotificationProperties.class);
                    assertThat(context).doesNotHaveBean("slackNotificationHttpClient");
                    assertThat(context).doesNotHaveBean("slackNotificationRestClient");
                });
    }

    @Test
    @DisplayName("알림 발송을 끄면 공통 알림 Bean을 등록하지 않는다")
    void create_deliveryDisabled_doesNotRegisterCommonNotificationBeans() {
        // When & Then
        contextRunner
                .withPropertyValues(
                        "chalkak.admin.notification.delivery-enabled=false",
                        "chalkak.admin.notification.admin-web-base-url=http://localhost:3100"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NotificationProperties.class);
                    assertThat(context).doesNotHaveBean(NotificationMessageFactory.class);
                    assertThat(context).doesNotHaveBean(
                            PostModerationPendingNotificationListener.class
                    );
                });
    }
}
