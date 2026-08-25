package com.chalkak.backend.user.api.internal.v1.controller;

import com.chalkak.backend.user.api.internal.v1.docs.SignatureProcessingCallbackApiDocs;
import com.chalkak.backend.user.infrastructure.infra.ProcessingCallbackAuthenticator;
import com.chalkak.backend.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(SignatureProcessingCallbackController.CALLBACK_PATH)
/**
 * 인증 헤더를 {@code required = false}로 받는 이유는, 누락을 400이 아니라 인증 실패인 401로 다루기 위해서다.
 * 서명 실패는 secret 불일치나 시계 차를 뜻하는 즉시 알람 대상이므로 일반 요청 오류와 섞이면 안 된다.
 */
public class SignatureProcessingCallbackController
        implements SignatureProcessingCallbackApiDocs {

    static final String CALLBACK_PATH = "/internal/v1/signature-processing";

    private static final String TIMESTAMP_HEADER = "X-Chalkak-Callback-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Chalkak-Callback-Signature";

    private final UserService userService;
    private final ProcessingCallbackAuthenticator authenticator;

    @Override
    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<Void> complete(
            @PathVariable UUID uploadId,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature
    ) {
        authenticator.authenticate(callbackPath(uploadId, "complete"), null, timestamp, signature);
        userService.completeSignatureProcessing(uploadId);

        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{uploadId}/failed")
    public ResponseEntity<Void> fail(
            @PathVariable UUID uploadId,
            @RequestHeader(value = TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature
    ) {
        authenticator.authenticate(callbackPath(uploadId, "failed"), null, timestamp, signature);
        userService.failSignatureProcessing(uploadId);

        return ResponseEntity.noContent().build();
    }

    private String callbackPath(UUID uploadId, String result) {
        return CALLBACK_PATH + "/" + uploadId + "/" + result;
    }
}
