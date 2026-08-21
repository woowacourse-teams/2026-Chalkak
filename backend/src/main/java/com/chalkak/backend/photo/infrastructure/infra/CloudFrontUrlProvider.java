package com.chalkak.backend.photo.infrastructure.infra;

import com.chalkak.backend.photo.service.ImageUrlProvider;
import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CloudFrontUrlProvider implements ImageUrlProvider {

    private final URI baseUrl;
    private final String originPath;

    public CloudFrontUrlProvider(
            @Value("${CLOUDFRONT_BASE_URL:https://dx6imgwswqps9.cloudfront.net}") URI baseUrl,
            @Value("${CLOUDFRONT_ORIGIN_PATH:/chalkak}") String originPath
    ) {
        this.baseUrl = baseUrl;
        this.originPath = originPath;
    }

    @Override
    public String getUrl(String storageKey) {
        if (storageKey == null) {
            return null;
        }

        String normalizedStorageKey = normalizePath(storageKey);
        if (normalizedStorageKey.isBlank()) {
            throw new IllegalArgumentException("스토리지 키는 비어 있을 수 없습니다.");
        }

        String publicPath = removeOriginPath(normalizedStorageKey);
        String[] pathSegments = Arrays.stream(publicPath.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);

        return UriComponentsBuilder.fromUri(baseUrl)
                .pathSegment(pathSegments)
                .build()
                .encode()
                .toUriString();
    }

    private String removeOriginPath(String storageKey) {
        String normalizedOriginPath = normalizePath(originPath);

        if (normalizedOriginPath.isBlank()) {
            return storageKey;
        }
        if (storageKey.startsWith(normalizedOriginPath + "/")) {
            return storageKey.substring(normalizedOriginPath.length() + 1);
        }

        throw new IllegalArgumentException(
                "스토리지 키는 원본 경로로 시작해야 합니다: /" + normalizedOriginPath
        );
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }

        return path.trim().replaceAll("^/+|/+$", "");
    }
}
