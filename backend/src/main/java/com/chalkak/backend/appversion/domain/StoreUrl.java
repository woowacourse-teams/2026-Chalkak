package com.chalkak.backend.appversion.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Embeddable
public record StoreUrl(
        @Column(name = "store_url", nullable = false, columnDefinition = "text") String value
) {

    private static final int MAX_LENGTH = 2_000;

    public StoreUrl {
        value = value == null ? "" : value.strip();
        if (value.isBlank() || value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw createInvalidUrlException();
        }
        URI uri = parse(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || !isValidPath(uri) || uri.getUserInfo() != null
                || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || !uri.normalize().getPath().equals(uri.getPath())
                || (!isAndroid(uri) && !isIos(uri))) {
            throw createInvalidUrlException();
        }
    }

    public void validatePlatform(AppPlatform platform) {
        URI uri = parse(value);
        if (platform == AppPlatform.ANDROID && isAndroid(uri)) {
            return;
        }
        if (platform == AppPlatform.IOS && isIos(uri)) {
            return;
        }
        throw createInvalidUrlException();
    }

    private static boolean isValidPath(URI uri) {
        String path = uri.getPath();
        return path != null && !path.contains("\\")
                && !uri.getRawPath().matches("(?i).*%2f.*")
                && Arrays.stream(path.split("/"))
                        .noneMatch(part -> ".".equals(part) || "..".equals(part));
    }

    private static boolean isAndroid(URI uri) {
        Map<String, String> query = parseQuery(uri);
        String id = query.get("id");
        return "play.google.com".equalsIgnoreCase(uri.getHost())
                && "/store/apps/details".equals(uri.getPath())
                && id != null && id.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
                && Set.of(
                        "id",
                        "hl",
                        "gl"
                ).containsAll(query.keySet())
                && hasLanguage(query, "hl") && hasLanguage(query, "gl");
    }

    private static boolean isIos(URI uri) {
        Map<String, String> query = parseQuery(uri);
        return "apps.apple.com".equalsIgnoreCase(uri.getHost())
                && uri.getPath().matches("/(?:[a-zA-Z]{2}/)?app/(?:[^/]+/)?id[1-9][0-9]*")
                && Set.of("l", "mt").containsAll(query.keySet())
                && hasLanguage(query, "l")
                && (!query.containsKey("mt") || "8".equals(query.get("mt")));
    }

    private static boolean hasLanguage(Map<String, String> query, String name) {
        return !query.containsKey(name) || query.get(name).matches("[A-Za-z_-]{2,10}");
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> values = new HashMap<>();
        if (uri.getRawQuery() == null) {
            return values;
        }
        for (String part : uri.getRawQuery().split("&", -1)) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) {
                throw createInvalidUrlException();
            }
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            if (values.putIfAbsent(key, value) != null) {
                throw createInvalidUrlException();
            }
        }
        return values;
    }

    private static URI parse(String value) {
        try {
            return new URI(value).parseServerAuthority();
        } catch (URISyntaxException exception) {
            throw createInvalidUrlException();
        }
    }

    private static BusinessException createInvalidUrlException() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR,
                "스토어 주소는 해당 플랫폼의 공식 HTTPS 앱 상세 주소여야 합니다.");
    }
}
