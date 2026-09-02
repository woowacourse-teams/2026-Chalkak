package com.chalkak.backend.admin.api.support;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chalkak.admin.cors")
public record AdminCorsProperties(List<String> allowedOrigins) {

    public AdminCorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("관리자 웹 허용 Origin이 필요합니다.");
        }
        allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .toList();
        if (allowedOrigins.stream().anyMatch(origin -> origin.isBlank() || origin.contains("*"))) {
            throw new IllegalArgumentException("관리자 웹 허용 Origin은 정확한 주소여야 합니다.");
        }
    }
}
