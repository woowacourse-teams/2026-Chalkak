package com.chalkak.backend.user.infrastructure.infra;

import com.chalkak.backend.user.domain.SignatureImageUpload;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.SignatureImageUploadIssuer;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@RequiredArgsConstructor
public class S3SignatureImageUploadIssuer implements SignatureImageUploadIssuer {

    private static final String CONTENT_TYPE = "image/png";
    private static final Duration SIGNATURE_DURATION = Duration.ofMinutes(5);

    private final S3Presigner s3Presigner;
    private final SignatureImageStorage signatureImageStorage;
    private final ImageProperties imageProperties;

    @Override
    public SignatureImageUpload issue(UUID uploadId) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(imageProperties.bucket())
                .key(signatureImageStorage.toStagingStorageKey(uploadId))
                .contentType(CONTENT_TYPE)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(SIGNATURE_DURATION)
                .putObjectRequest(putObjectRequest)
                .build();
        String uploadUrl = s3Presigner.presignPutObject(presignRequest)
                .url()
                .toString();

        return new SignatureImageUpload(
                uploadId,
                uploadUrl,
                SIGNATURE_DURATION.toSeconds()
        );
    }
}
