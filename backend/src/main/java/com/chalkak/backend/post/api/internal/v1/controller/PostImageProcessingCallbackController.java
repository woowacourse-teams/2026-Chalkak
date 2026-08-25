package com.chalkak.backend.post.api.internal.v1.controller;

import com.chalkak.backend.auth.api.support.ProcessingCallbackAuthenticator;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.post.api.internal.v1.docs.PostImageProcessingCallbackApiDocs;
import com.chalkak.backend.post.api.internal.v1.dto.request.PostImageProcessingCompleteRequest;
import com.chalkak.backend.post.api.internal.v1.dto.request.PostImageProcessingFailRequest;
import com.chalkak.backend.post.service.PostService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * 본문을 DTO가 아니라 원문 문자열로 받는 이유는 서명 검증 때문이다. 역직렬화 후 다시 직렬화한 문자열은 공백과
 * 키 순서가 달라져 Lambda가 계산한 해시와 어긋난다. 인증을 통과한 뒤에만 파싱한다.
 *
 * <p>인증 헤더를 {@code required = false}로 받는 이유는 누락을 400이 아니라 401로 다루기 위해서다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(PostImageProcessingCallbackController.CALLBACK_PATH)
public class PostImageProcessingCallbackController
        implements PostImageProcessingCallbackApiDocs {

    static final String CALLBACK_PATH = "/internal/v1/post-image-processing";

    private static final String TIMESTAMP_HEADER = "X-Chalkak-Callback-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Chalkak-Callback-Signature";

    private final PostService postService;
    private final ProcessingCallbackAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    @Override
    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<Void> complete(
            @PathVariable UUID uploadId,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody(required = false) String rawBody
    ) {
        authenticator.authenticate(
                callbackPath(uploadId, "complete"),
                rawBody,
                timestamp,
                signature
        );
        PostImageProcessingCompleteRequest request =
                readBody(rawBody, PostImageProcessingCompleteRequest.class);
        postService.completePostImageProcessing(uploadId, request.toMetadata());

        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{uploadId}/failed")
    public ResponseEntity<Void> fail(
            @PathVariable UUID uploadId,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody(required = false) String rawBody
    ) {
        authenticator.authenticate(
                callbackPath(uploadId, "failed"),
                rawBody,
                timestamp,
                signature
        );
        PostImageProcessingFailRequest request =
                readBody(rawBody, PostImageProcessingFailRequest.class);
        postService.failPostImageProcessing(uploadId, request.reason());

        return ResponseEntity.noContent().build();
    }

    private <T> T readBody(String rawBody, Class<T> type) {
        try {
            return objectMapper.readValue(rawBody, type);
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이미지 처리 콜백 본문을 읽을 수 없습니다."
            );
        }
    }

    private String callbackPath(UUID uploadId, String result) {
        return CALLBACK_PATH + "/" + uploadId + "/" + result;
    }
}
