package com.chalkak.backend.user.api.internal.v1.controller;

import com.chalkak.backend.user.infrastructure.infra.SignatureProcessingCallbackAuthenticator;
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
@RequestMapping("/internal/v1/signature-processing")
public class SignatureProcessingCallbackController {

    private static final String TIMESTAMP_HEADER = "X-Chalkak-Callback-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Chalkak-Callback-Signature";

    private final UserService userService;
    private final SignatureProcessingCallbackAuthenticator authenticator;

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<Void> complete(
            @PathVariable UUID uploadId,
            @RequestHeader(TIMESTAMP_HEADER) String timestamp,
            @RequestHeader(SIGNATURE_HEADER) String signature
    ) {
        authenticator.authenticate(uploadId, "complete", timestamp, signature);
        userService.completeSignatureProcessing(uploadId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uploadId}/failed")
    public ResponseEntity<Void> fail(
            @PathVariable UUID uploadId,
            @RequestHeader(TIMESTAMP_HEADER) String timestamp,
            @RequestHeader(SIGNATURE_HEADER) String signature
    ) {
        authenticator.authenticate(uploadId, "failed", timestamp, signature);
        userService.failSignatureProcessing(uploadId);

        return ResponseEntity.noContent().build();
    }
}
