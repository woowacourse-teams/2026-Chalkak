package com.chalkak.backend.auth.domain;

/**
 * 새로 만든 리프레시 토큰. value는 클라이언트에게만 전달하고 서버에는 tokenHash만 남긴다.
 */
public record GeneratedRefreshToken(
        String value,
        String tokenHash
) {
}
