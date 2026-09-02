package com.chalkak.backend.user.infrastructure.infra;

import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.repository.SignatureImageUploadIssuer;
import com.chalkak.backend.user.repository.SignatureProcessingImageUpload;
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
        String uploadUrl = presign(putObjectRequest);

        return new SignatureImageUpload(
                uploadId,
                uploadUrl,
                SIGNATURE_DURATION.toSeconds()
        );
    }

    @Override
    public SignatureProcessingImageUpload issueProcessingUpload(UUID uploadId) {
        SignatureStorageKeys storageKeys = signatureImageStorage.toStorageKeys(uploadId);
        String originalUploadUrl = presignProcessingUpload(
                storageKeys.originalStorageKey()
        );
        String thumbnailUploadUrl = presignProcessingUpload(
                storageKeys.thumbnailStorageKey()
        );

        return new SignatureProcessingImageUpload(
                originalUploadUrl,
                thumbnailUploadUrl,
                CONTENT_TYPE,
                imageProperties.signature().cacheControl()
        );
    }

    private String presignProcessingUpload(String storageKey) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(imageProperties.bucket())
                .key(storageKey)
                .contentType(CONTENT_TYPE)
                .cacheControl(imageProperties.signature().cacheControl())
                .ifNoneMatch("*")
                .build();
        return presign(putObjectRequest);
    }

    private String presign(PutObjectRequest putObjectRequest) {
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(SIGNATURE_DURATION)
                .putObjectRequest(putObjectRequest)
                .build();
        return s3Presigner.presignPutObject(presignRequest)
                .url()
                .toString();
    }
}
