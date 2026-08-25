package com.chalkak.backend.post.infrastructure.infra;

import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.user.infrastructure.infra.ImageProperties;
import java.net.HttpURLConnection;
import java.util.UUID;
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
    private static final String IMAGE_EXTENSION = ".png";
    private static final String PATH_DELIMITER = "/";

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
}
