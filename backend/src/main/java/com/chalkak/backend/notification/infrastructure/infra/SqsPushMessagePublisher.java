package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.service.PushMessage;
import com.chalkak.backend.notification.service.PushMessagePublisher;
import com.chalkak.backend.notification.service.PushPublicationResult;
import com.chalkak.backend.notification.service.TopicPushTracing;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class SqsPushMessagePublisher implements PushMessagePublisher {

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;
    private final TopicPushTracing tracing;

    public SqsPushMessagePublisher(
            SqsClient sqsClient,
            String queueUrl,
            ObjectMapper objectMapper,
            TopicPushTracing tracing
    ) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = objectMapper;
        this.tracing = tracing;
    }

    @Override
    public PushPublicationResult publish(PushMessage message) {
        if (message.type() != NotificationType.TOPIC) {
            return send(message, Map.of());
        }
        try (var trace = tracing.startPublish()) {
            trace.setEventId(message.eventId());
            Map<String, String> headers = new HashMap<>();
            trace.writeHeaders(headers);
            Map<String, MessageAttributeValue> attributes = new HashMap<>();
            headers.forEach((key, value) -> attributes.put(key, MessageAttributeValue.builder()
                    .dataType("String").stringValue(value).build()));
            PushPublicationResult result = send(message, attributes);
            trace.recordOutcome(result.status().name(), !result.isAccepted());
            return result;
        }
    }

    private PushPublicationResult send(PushMessage message,
            Map<String, MessageAttributeValue> attributes) {
        try {
            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(message))
                    .messageAttributes(attributes)
                    .build()).messageId();
            if (messageId == null || messageId.isBlank()) {
                return PushPublicationResult.unknown("SQS_ACCEPTANCE_UNKNOWN");
            }
            return PushPublicationResult.accepted(messageId);
        } catch (SqsException exception) {
            String errorCode = getSafeErrorCode(exception);
            if (exception.statusCode() >= 500 || exception.statusCode() < 400) {
                return PushPublicationResult.unknown(errorCode);
            }
            if (exception.isThrottlingException() || exception.statusCode() == 429) {
                return PushPublicationResult.retry(errorCode);
            }
            return PushPublicationResult.failed(errorCode);
        } catch (SdkClientException exception) {
            // 응답을 확인하지 못했으므로 수락 여부를 보수적으로 불명 처리한다.
            return PushPublicationResult.unknown("SQS_CLIENT_ERROR");
        } catch (JacksonException exception) {
            return PushPublicationResult.failed("SQS_MESSAGE_SERIALIZATION_ERROR");
        }
    }

    private String getSafeErrorCode(SqsException exception) {
        if (exception.awsErrorDetails() != null) {
            String errorCode = exception.awsErrorDetails().errorCode();
            if (errorCode != null && errorCode.matches("[A-Za-z0-9._-]{1,100}")) {
                return errorCode;
            }
        }
        return "SQS_SERVICE_ERROR";
    }
}
