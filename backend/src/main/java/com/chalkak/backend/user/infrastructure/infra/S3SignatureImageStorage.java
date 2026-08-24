package com.chalkak.backend.user.infrastructure.infra;

import com.chalkak.backend.user.domain.StoredImageMetadata;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@RequiredArgsConstructor
public class S3SignatureImageStorage implements SignatureImageStorage {

    private static final String STAGING_PATH = "staging/signatures";
    private static final String ORIGINAL_PATH = "signatures/original";
    private static final String IMAGE_EXTENSION = ".png";
    private static final String PATH_DELIMITER = "/";

    private final S3Client s3Client;
    private final ImageProperties imageProperties;

    @Override
    public Optional<StoredImageMetadata> findUploadedImage(UUID uploadId) {
        S3Exception stagingFailure = null;
        try {
            Optional<StoredImageMetadata> stagingImage = findImage(toStagingStorageKey(uploadId));
            if (stagingImage.isPresent()) {
                return stagingImage;
            }
        } catch (S3Exception exception) {
            stagingFailure = exception;
        }
        Optional<StoredImageMetadata> originalImage = findImage(toOriginalStorageKey(uploadId));
        if (originalImage.isEmpty() && stagingFailure != null) {
            throw stagingFailure;
        }
        return originalImage;
    }

    @Override
    public String toOriginalStorageKey(UUID uploadId) {
        return createKey(ORIGINAL_PATH, uploadId);
    }

    @Override
    public String toImageUrl(String storageKey) {
        String publicPath = removeRootPrefix(storageKey);
        String baseUrl = imageProperties.baseUrl();
        if (baseUrl.endsWith(PATH_DELIMITER)) {
            return baseUrl + publicPath;
        }
        return baseUrl + PATH_DELIMITER + publicPath;
    }

    private Optional<StoredImageMetadata> findImage(String storageKey) {
        try {
            HeadObjectResponse response = s3Client.headObject(request -> request
                    .bucket(imageProperties.bucket())
                    .key(storageKey));
            return Optional.of(new StoredImageMetadata(response.contentType(), response.contentLength()));
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (S3Exception exception) {
            // s3:ListBucket 권한이 없으면 S3가 객체 존재를 숨기려고 404 대신 403을 준다.
            // 호출자 입장에서 둘을 구분할 수 없으므로 없는 것으로 본다.
            if (exception.statusCode() == HttpURLConnection.HTTP_FORBIDDEN) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    /**
     * CloudFront 오리진이 {@code {bucket}/{root-prefix}}를 가리키므로 공개 URL에는 root-prefix가 들어가지 않는다.
     */
    private String removeRootPrefix(String storageKey) {
        String rootPrefix = imageProperties.rootPrefix();
        if (!storageKey.startsWith(rootPrefix + PATH_DELIMITER)) {
            throw new IllegalArgumentException("스토리지 키는 root-prefix로 시작해야 합니다: " + rootPrefix);
        }
        return storageKey.substring(rootPrefix.length() + PATH_DELIMITER.length());
    }

    private String toStagingStorageKey(UUID uploadId) {
        return createKey(STAGING_PATH, uploadId);
    }

    private String createKey(String path, UUID uploadId) {
        return String.join(
                PATH_DELIMITER,
                imageProperties.rootPrefix(),
                path,
                uploadId + IMAGE_EXTENSION
        );
    }
}
