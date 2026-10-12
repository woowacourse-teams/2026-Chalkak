package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.PushMessage;
import com.chalkak.backend.notification.service.PushMessagePublisher;
import com.chalkak.backend.notification.service.PushPublicationResult;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class SqsPushMessagePublisher implements PushMessagePublisher {

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public SqsPushMessagePublisher(
            SqsClient sqsClient,
            String queueUrl,
            ObjectMapper objectMapper
    ) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = objectMapper;
    }

    @Override
    public PushPublicationResult publish(PushMessage message) {
        try {
            String messageId = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(message))
                    .build()).messageId();
            if (messageId == null || messageId.isBlank()) {
                return PushPublicationResult.retry("SQS_ACCEPTANCE_UNKNOWN");
            }
            return PushPublicationResult.accepted(messageId);
        } catch (SqsException exception) {
            String errorCode = getSafeErrorCode(exception);
            if (exception.statusCode() >= 500 || exception.isThrottlingException()
                    || exception.statusCode() == 429 || exception.statusCode() < 400) {
                return PushPublicationResult.retry(errorCode);
            }
            return PushPublicationResult.failed(errorCode);
        } catch (SdkClientException exception) {
            // 재시도 가능 여부만 반환한다. Relay는 DB에서 재발행하고, 주제는 최종 결과 불명으로 종료한다.
            return PushPublicationResult.retry("SQS_CLIENT_ERROR");
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
