package com.chalkak.backend.jev.api.internal.v1.controller;

import com.chalkak.backend.auth.api.support.ProcessingCallbackAuthenticator;
import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.jev.api.internal.v1.docs.JevJobCallbackApiDocs;
import com.chalkak.backend.jev.api.internal.v1.dto.request.JevJobClaimRequest;
import com.chalkak.backend.jev.api.internal.v1.dto.request.JevJobResultRequest;
import com.chalkak.backend.jev.api.internal.v1.dto.response.JevJobClaimResponse;
import com.chalkak.backend.jev.domain.JevCallResult;
import com.chalkak.backend.jev.service.JevJobService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/jev-moderation-jobs")
public class JevJobCallbackController implements JevJobCallbackApiDocs {

    private final ProcessingCallbackAuthenticator authenticator;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final JevJobService service;

    @Override
    @PostMapping("/claims")
    public ResponseEntity<JevJobClaimResponse> claim(
            @RequestHeader(value = "X-Chalkak-Callback-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Chalkak-Callback-Signature", required = false) String signature,
            @RequestBody(required = false) byte[] rawBody
    ) {
        authenticator.authenticate(
                "/internal/v1/jev-moderation-jobs/claims",
                rawBody,
                timestamp,
                signature
        );
        JevJobClaimRequest request = readBody(rawBody, JevJobClaimRequest.class);
        return ResponseEntity.ok(JevJobClaimResponse
                .from(service.claim(CanonicalUuidParser.parse(request.jobId()))));
    }

    @Override
    @PostMapping("/{jobId}/results")
    public ResponseEntity<Void> receiveResult(
            @PathVariable String jobId,
            @RequestHeader(value = "X-Chalkak-Callback-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Chalkak-Callback-Signature", required = false) String signature,
            @RequestBody(required = false) byte[] rawBody
    ) {
        var id = CanonicalUuidParser.parse(jobId);
        authenticator.authenticate(
                "/internal/v1/jev-moderation-jobs/" + id + "/results",
                rawBody,
                timestamp,
                signature
        );
        JevJobResultRequest request = readBody(rawBody, JevJobResultRequest.class);
        if (request.errorCode() == null
                && (request.rawResponse() == null
                        || !Integer.valueOf(200).equals(request.httpStatus()))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Jev 성공 응답 본문과 HTTP 200이 필요합니다.");
        }
        service.receiveResult(
                id,
                request.stage(),
                new JevCallResult(
                        CanonicalUuidParser.parse(request.callId()),
                        null,
                        null,
                        request.rawResponse(),
                        request.errorCode(),
                        request.httpStatus(),
                        request.latencyMs(),
                        request.retryAfterSeconds()
                )
        );
        return ResponseEntity.noContent().build();
    }

    private <T> T readBody(byte[] rawBody, Class<T> type) {
        T value;
        try {
            value = objectMapper.readValue(rawBody, type);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Jev 콜백 본문을 읽을 수 없습니다.");
        }
        if (value == null || !validator.validate(value).isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Jev 콜백 본문이 올바르지 않습니다.");
        }
        return value;
    }
}
