package com.chalkak.backend.post.infrastructure.infra;

import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PostImageUploadIssuer;
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
 * presigned PUT은 용량을 강제하지 못한다. SigV4 query presigning이 host 외의 헤더를 서명하지 않으므로
 * {@code maxBytes}는 클라이언트가 전송 전에 거를 수 있게 알려주는 값이고, 실제 상한은 이미지 처리 Lambda가
 * 최종 판정한다.
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
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(SIGNATURE_DURATION)
                .putObjectRequest(putObjectRequest)
                .build();
        String uploadUrl = s3Presigner.presignPutObject(presignRequest)
                .url()
                .toString();

        return new PresignedPostImageUpload(
                uploadUrl,
                SIGNATURE_DURATION.toSeconds(),
                CONTENT_TYPE,
                imageProperties.post().maxBytes()
        );
    }
}
