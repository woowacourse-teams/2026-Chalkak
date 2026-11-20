package com.chalkak.backend.jev.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.json.JsonMapper;

class JevQueueConfigurationTest {

    @Test
    @DisplayName("큐에는 환경과 작업 ID만 보내며 프롬프트나 사진 원문을 싣지 않는다")
    void publish_validQueue_sendsTypedJobReference() {
        // Given
        SqsClient client = mock(SqsClient.class);
        JsonMapper mapper = JsonMapper.builder().build();
        var publisher = new JevQueueConfiguration().jevJobPublisher(
                client,
                "https://sqs.ap-northeast-2.amazonaws.com/123456789012/jev-jobs",
                "dev",
                mapper
        );
        UUID id = UUID.randomUUID();
        // When
        publisher.publish(id);
        // Then
        ArgumentCaptor<SendMessageRequest> request = ArgumentCaptor
                .forClass(SendMessageRequest.class);
        verify(client).sendMessage(request.capture());
        var payload = mapper.readTree(request.getValue().messageBody());
        assertThat(payload.size()).isEqualTo(3);
        assertThat(payload.get("type").stringValue()).isEqualTo("JEV_MODERATION");
        assertThat(payload.get("jobId").stringValue()).isEqualTo(id.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://sqs.evil.test/jobs",
            "https://sqs.ap-northeast-2.amazonaws.com/123456789012/jev.fifo"})
    @DisplayName("잘못된 큐 주소와 FIFO 큐는 기동 시 거절한다")
    void publisher_invalidQueue_rejects(String queue) {
        // When & Then
        assertThatThrownBy(() -> new JevQueueConfiguration().jevJobPublisher(
                mock(SqsClient.class),
                queue,
                "dev",
                JsonMapper.builder().build()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
