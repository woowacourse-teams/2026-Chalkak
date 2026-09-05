package com.chalkak.backend.user.infrastructure.infra;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chalkak.image")
public record ImageProperties(
        String bucket,
        String region,
        String baseUrl,
        @NotBlank
        @Pattern(
                regexp = "^[^/]+(/[^/]+)*$",
                message = "root-prefix는 앞뒤 슬래시와 빈 구간 없이 작성해야 합니다."
        )
        String rootPrefix,
        @NotBlank
        @Pattern(
                regexp = "^(dev|prod|local|test)$",
                message = "environment는 dev, prod, local, test 중 하나여야 합니다."
        )
        String environment,
        Signature signature,
        Post post,
        boolean anonymousAccess
) {

    public record Signature(
            long maxBytes,
            List<String> allowedContentTypes,
            String cacheControl
    ) {
    }

    public record Post(long maxBytes, String cacheControl) {
    }
}
