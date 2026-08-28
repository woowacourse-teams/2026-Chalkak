package com.chalkak.backend.post.infrastructure.infra;

import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PostImageUploadIssuer;
import com.chalkak.backend.post.repository.PostProcessingImageUpload;
import com.chalkak.backend.post.repository.PresignedPostImageUpload;
import com.chalkak.backend.user.infrastructure.infra.ImageProperties;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * presigned PUT은 용량을 강제하지 못한다. {@code contentType}은 {@code X-Amz-SignedHeaders}에 포함돼 실제로
 * 강제되지만 {@code content-length}는 서명 대상이 아니라서, {@code maxBytes}는 클라이언트가 전송 전에 거를 수
 * 있게 알려주는 값이고 실제 상한은 이미지 처리 Lambda가 최종 판정한다.
 *
 * <p>용량까지 강제하려면 {@code content-length-range} 조건을 담을 수 있는 POST policy로 바꿔야 한다.
 */
@Component
@RequiredArgsConstructor
public class S3PostImageUploadIssuer implements PostImageUploadIssuer {

    private static final String CONTENT_TYPE = "image/webp";
    private static final Duration SIGNATURE_DURATION = Duration.ofMinutes(5);

    private final S3Presigner s3Presigner;
    private final PostImageStorage postImageStorage;
    private final ImageProperties imageProperties;

    @Override
    public PresignedPostImageUpload issue(UUID uploadId) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(imageProperties.bucket())
                .key(postImageStorage.toStagingStorageKey(uploadId))
                .contentType(CONTENT_TYPE)
                .build();
        String uploadUrl = presign(putObjectRequest);

        return new PresignedPostImageUpload(
                uploadUrl,
                SIGNATURE_DURATION.toSeconds(),
                CONTENT_TYPE,
                imageProperties.post().maxBytes()
        );
    }

    @Override
    public PostProcessingImageUpload issueProcessingUpload(UUID uploadId) {
        String originalUploadUrl = presignProcessingUpload(
                postImageStorage.toOriginalStorageKey(uploadId)
        );
        String thumbnailUploadUrl = presignProcessingUpload(
                postImageStorage.toThumbnailStorageKey(uploadId)
        );

        return new PostProcessingImageUpload(
                originalUploadUrl,
                thumbnailUploadUrl,
                CONTENT_TYPE,
                imageProperties.post().cacheControl()
        );
    }

    @Override
    public Duration processingUploadUrlValidity() {
        return SIGNATURE_DURATION;
    }

    private String presignProcessingUpload(String storageKey) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(imageProperties.bucket())
                .key(storageKey)
                .contentType(CONTENT_TYPE)
                .cacheControl(imageProperties.post().cacheControl())
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
