package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.notification.service.NotificationRelayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SqsPushConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SqsPushConfiguration.class, NotificationRelayScheduler.class)
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withBean(NotificationRelayService.class, () -> mock(NotificationRelayService.class))
            .withPropertyValues("chalkak.notification.sqs.region=ap-northeast-2");

    @Test
    @DisplayName("Relay가 꺼져 있으면 AWS 자격증명이나 큐 없이 기동하고 스케줄러는 만들지 않는다")
    void configuration_disabled_startsWithoutRelayScheduler() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.notification.sqs.relay-enabled=false",
                        "chalkak.notification.sqs.queue-url=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NotificationRelayScheduler.class);
                });
    }

    @Test
    @DisplayName("Relay를 켜고 Standard 큐 URL을 지정하면 스케줄러를 만든다")
    void configuration_enabledWithQueue_createsRelayScheduler() {
        // When & Then
        contextRunner.withPropertyValues("chalkak.notification.sqs.relay-enabled=true",
                "chalkak.notification.sqs.queue-url=https://sqs.ap-northeast-2.amazonaws.com/000000000000/chalkak-dev-push")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NotificationRelayScheduler.class);
                });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "REPLACE_WITH_QUEUE",
            "http://sqs.ap-northeast-2.amazonaws.com/000000000000/push",
            "https://sqs.ap-northeast-2.amazonaws.com/000000000000/push.fifo"})
    @DisplayName("Relay를 켜면 누락·부적절한 URL·FIFO 큐 설정을 기동 때 거부한다")
    void configuration_enabledWithInvalidQueue_rejectsConfiguration(String url) {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.notification.sqs.relay-enabled=true",
                        "chalkak.notification.sqs.queue-url=" + url)
                .run(context -> assertThat(context).hasFailed());
    }
}
