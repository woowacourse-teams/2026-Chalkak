package com.chalkak.backend.user.service;

import java.util.UUID;

/**
 * 외부 제공자에서 폐기한 소셜 연결의 DB 상태다. credentialState는 제공자별 인증 값을
 * 해석하지 않고, 탈퇴 트랜잭션에서 현재 상태와 같은지 대조하는 용도로만 사용한다.
 */
public record SocialConnectionRevocationSnapshot(
        UUID authorizationId,
        String credentialState
) {
}
