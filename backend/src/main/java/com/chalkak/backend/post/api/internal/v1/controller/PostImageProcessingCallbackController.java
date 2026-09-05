package com.chalkak.backend.post.api.internal.v1.controller;

import com.chalkak.backend.auth.api.support.ProcessingCallbackAuthenticator;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.post.api.internal.v1.docs.PostImageProcessingCallbackApiDocs;
import com.chalkak.backend.post.api.internal.v1.dto.request.PostImageProcessingCompleteRequest;
import com.chalkak.backend.post.api.internal.v1.dto.request.PostImageProcessingFailRequest;
import com.chalkak.backend.post.api.internal.v1.dto.response.PostProcessingUploadUrlsResponse;
import com.chalkak.backend.post.service.PostCommandService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * 본문을 DTO가 아니라 원문 바이트로 받는 이유는 서명 검증 때문이다. 역직렬화 후 다시 직렬화한 문자열은 공백과
 * 키 순서가 달라져 Lambda가 계산한 해시와 어긋난다. 인증을 통과한 뒤에만 파싱한다.
 *
 * <p>인증 헤더를 {@code required = false}로 받는 이유는 누락을 400이 아니라 401로 다루기 위해서다.
 *
 * <p>원문을 직접 파싱하는 탓에 {@code @Valid}가 걸리지 않으므로 검증기를 직접 호출한다. 본문에 실리는 값의
 * 상당 부분이 공격자가 만든 파일의 EXIF에서 오기 때문에 인증만으로는 충분하지 않다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(PostImageProcessingCallbackController.CALLBACK_PATH)
public class PostImageProcessingCallbackController
        implements PostImageProcessingCallbackApiDocs {

    static final String CALLBACK_PATH = "/internal/v1/post-image-processing";

    private static final String TIMESTAMP_HEADER = "X-Chalkak-Callback-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Chalkak-Callback-Signature";

    private final PostCommandService postCommandService;
    private final ProcessingCallbackAuthenticator authenticator;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Override
    @PostMapping("/{uploadId}/upload-urls")
    public ResponseEntity<PostProcessingUploadUrlsResponse> issueUploadUrls(
            @PathVariable String uploadId,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature
    ) {
        UUID parsedUploadId = CanonicalUuidParser.parse(uploadId);
        authenticator.authenticate(
                callbackPath(parsedUploadId, "upload-urls"),
                null,
                timestamp,
                signature
        );
        return ResponseEntity.ok(PostProcessingUploadUrlsResponse.from(
                postCommandService.issuePostImageProcessingUpload(parsedUploadId)
        ));
    }

    @Override
    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<Void> complete(
            @PathVariable String uploadId,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody(required = false) byte[] rawBody
    ) {
        UUID parsedUploadId = CanonicalUuidParser.parse(uploadId);
        authenticator.authenticate(
                callbackPath(parsedUploadId, "complete"),
                rawBody,
                timestamp,
                signature
        );
        PostImageProcessingCompleteRequest request =
                readBody(rawBody, PostImageProcessingCompleteRequest.class);
        postCommandService.completePostImageProcessing(parsedUploadId, request.toMetadata());

        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{uploadId}/failed")
    public ResponseEntity<Void> fail(
            @PathVariable String uploadId,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody(required = false) byte[] rawBody
    ) {
        UUID parsedUploadId = CanonicalUuidParser.parse(uploadId);
        authenticator.authenticate(
                callbackPath(parsedUploadId, "failed"),
                rawBody,
                timestamp,
                signature
        );
        PostImageProcessingFailRequest request =
                readBody(rawBody, PostImageProcessingFailRequest.class);
        postCommandService.failPostImageProcessing(parsedUploadId, request.reason());

        return ResponseEntity.noContent().build();
    }

    private <T> T readBody(byte[] rawBody, Class<T> type) {
        T request = parseBody(rawBody, type);
        validate(request);

        return request;
    }

    private <T> T parseBody(byte[] rawBody, Class<T> type) {
        try {
            return objectMapper.readValue(rawBody, type);
        } catch (Exception exception) {
            // 이 예외 하나로 콜백이 영구 실패하므로 원인을 남기지 않으면 추적할 방법이 없다.
            log.error("이미지 처리 콜백 본문을 파싱하지 못했습니다. type={}", type.getSimpleName(), exception);
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이미지 처리 콜백 본문을 읽을 수 없습니다."
            );
        }
    }

    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return;
        }
        String message = violations.iterator().next().getPropertyPath()
                + " " + violations.iterator().next().getMessage();
        log.error("이미지 처리 콜백 본문이 유효하지 않습니다. {}", message);
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "이미지 처리 콜백 본문이 올바르지 않습니다."
        );
    }

    private String callbackPath(UUID uploadId, String result) {
        return CALLBACK_PATH + "/" + uploadId + "/" + result;
    }
}
