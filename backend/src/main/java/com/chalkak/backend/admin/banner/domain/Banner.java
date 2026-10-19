package com.chalkak.backend.admin.banner.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "banners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner {

    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MAX_SUBTITLE_LENGTH = 100;
    private static final int MAX_URL_LENGTH = 2_000;

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, length = MAX_SUBTITLE_LENGTH)
    private String subtitle;

    @Column(name = "web_view_url", nullable = false, length = MAX_URL_LENGTH)
    private String webViewUrl;

    @Column(name = "thumbnail_url", nullable = false, length = MAX_URL_LENGTH)
    private String thumbnailUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Banner create(
            String title,
            String subtitle,
            String webViewUrl,
            String thumbnailUrl
    ) {
        Banner banner = new Banner();
        banner.title = normalizeText(title, MAX_TITLE_LENGTH, "배너 제목은 1자 이상 50자 이하여야 합니다.");
        banner.subtitle = normalizeText(subtitle, MAX_SUBTITLE_LENGTH,
                "배너 부제목은 1자 이상 100자 이하여야 합니다.");
        banner.webViewUrl = normalizeUrl(webViewUrl);
        banner.thumbnailUrl = normalizeUrl(thumbnailUrl);
        return banner;
    }

    private static String normalizeText(
            String value,
            int maxLength,
            String message
    ) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
        return normalized;
    }

    private static String normalizeUrl(String value) {
        String normalized = normalizeText(value, MAX_URL_LENGTH,
                "배너 URL은 1자 이상 2,000자 이하여야 합니다.");
        try {
            URI uri = new URI(normalized).parseServerAuthority();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw invalidUrlException();
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw invalidUrlException();
        }
    }

    private static BusinessException invalidUrlException() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR,
                "배너 URL은 호스트가 있는 HTTPS 절대 주소여야 합니다.");
    }
}
