package com.chalkak.backend.post.infrastructure.infra;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.user.infrastructure.infra.ImageProperties;
import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@RequiredArgsConstructor
public class S3PostImageStorage implements PostImageStorage {

    private static final String STAGING_PATH = "staging";
    private static final String POST_PATH = "posts";
    private static final String ORIGINAL_PATH = "original";
    private static final String THUMBNAIL_PATH = "thumbnail";
    private static final String IMAGE_EXTENSION = ".webp";
    private static final String PATH_DELIMITER = "/";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final S3Client s3Client;
    private final ImageProperties imageProperties;

    @Override
    public boolean existsUploadedImage(UUID uploadId) {
        try {
            s3Client.headObject(request -> request
                    .bucket(imageProperties.bucket())
                    .key(toStagingStorageKey(uploadId)));
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == HttpURLConnection.HTTP_FORBIDDEN
                    || exception.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    public String toStagingStorageKey(UUID uploadId) {
        return createKey(STAGING_PATH, imageProperties.environment(), POST_PATH, uploadId);
    }

    @Override
    public String toOriginalStorageKey(UUID uploadId) {
        return createKey(POST_PATH, imageProperties.environment(), ORIGINAL_PATH, uploadId);
    }

    @Override
    public String toThumbnailStorageKey(UUID uploadId) {
        return createKey(POST_PATH, imageProperties.environment(), THUMBNAIL_PATH, uploadId);
    }

    @Override
    public void deleteImage(String storageKey) {
        validateDeletableStorageKey(storageKey);
        try {
            s3Client.deleteObject(request -> request
                    .bucket(imageProperties.bucket())
                    .key(storageKey));
        } catch (NoSuchKeyException exception) {
            return;
        } catch (S3Exception exception) {
            if (exception.statusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                return;
            }
            throw exception;
        }
    }

    private String createKey(
            String firstPath,
            String secondPath,
            String thirdPath,
            UUID uploadId
    ) {
        return String.join(
                PATH_DELIMITER,
                imageProperties.rootPrefix(),
                firstPath,
                secondPath,
                thirdPath,
                uploadId + IMAGE_EXTENSION
        );
    }

    private void validateDeletableStorageKey(String storageKey) {
        if (storageKey == null || !isPostImageStorageKey(storageKey)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "삭제할 게시물 이미지 키가 올바르지 않습니다."
            );
        }
    }

    private boolean isPostImageStorageKey(String storageKey) {
        return matches(storageKey, STAGING_PATH, POST_PATH)
                || matches(storageKey, POST_PATH, ORIGINAL_PATH)
                || matches(storageKey, POST_PATH, THUMBNAIL_PATH);
    }

    private boolean matches(String storageKey, String firstPath, String thirdPath) {
        String prefix = String.join(
                PATH_DELIMITER,
                imageProperties.rootPrefix(),
                firstPath,
                imageProperties.environment(),
                thirdPath
        ) + PATH_DELIMITER;
        return Pattern.matches(
                Pattern.quote(prefix) + UUID_PATTERN + Pattern.quote(IMAGE_EXTENSION),
                storageKey
        );
    }
}
