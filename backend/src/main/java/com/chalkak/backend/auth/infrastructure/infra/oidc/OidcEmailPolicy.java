package com.chalkak.backend.auth.infrastructure.infra.oidc;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 제공자가 ID Token에 담아 준 이메일을 회원 정보로 받아들이는 기준이다. 제공자마다 이메일 소유 확인을 알려 주는 방식이 달라, ID
 * Token 검증 규칙 중 이것만 제공자별로 정한다.
 */
enum OidcEmailPolicy {

    /**
     * {@code email_verified}가 참일 때만 이메일을 쓴다. Apple은 이 값을 boolean 또는 문자열
     * "true"/"false"로 보내는데, {@code String.valueOf(Boolean.TRUE)}가 "true"이므로 문자열 비교
     * 하나로 두 표현을 모두 받는다.
     */
    VERIFIED_ONLY {
        @Override
        String extractEmail(Jwt jwt) {
            Object emailVerified = jwt.getClaim(EMAIL_VERIFIED_CLAIM);
            if (!"true".equalsIgnoreCase(String.valueOf(emailVerified))) {
                return null;
            }
            return jwt.getClaimAsString(EMAIL_CLAIM);
        }
    },

    /** 제공자가 준 이메일을 확인 여부와 관계없이 그대로 쓴다. */
    AS_PROVIDED {
        @Override
        String extractEmail(Jwt jwt) {
            return jwt.getClaimAsString(EMAIL_CLAIM);
        }
    };

    private static final String EMAIL_CLAIM = "email";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    abstract String extractEmail(Jwt jwt);
}
