package com.chalkak.backend.auth.service;

import java.util.UUID;

/**
 * 탈퇴 시점에 폐기 대상으로 조회한 Apple 인증 정보 한 건이다. Apple 폐기 요청에는 저장된
 * clientId가 필요하고, 삭제 직전에는 이 스냅샷과 DB의 현재 상태가 같은지 대조해야 하므로
 * 세 값을 함께 들고 다닌다.
 */
public record AppleAuthorizationSnapshot(
        UUID id,
        String clientId,
        String encryptedRefreshToken
) {
}
