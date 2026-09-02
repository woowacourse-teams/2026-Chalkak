package com.chalkak.backend.auth.repository;

import com.chalkak.backend.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 회원과 관리자의 리프레시 토큰 저장소가 공유하는 포트. 두 테이블은 각자 실제 FK를 유지하려고
 * 나뉘어 있지만 회전과 폐기 절차는 같으므로, 회전 서비스가 소유자 종류와 무관하게 동작하도록 묶는다.
 */
public interface RefreshTokenRepository<T extends RefreshToken> {

    /**
     * 회전 대상 토큰을 잠금과 함께 조회한다. 같은 토큰으로 동시에 들어온 재발급 요청이 서로 다른
     * 후속 토큰을 만들지 않도록 행 잠금이 필요하다.
     */
    Optional<T> findByTokenHashForUpdate(String tokenHash);

    T save(T refreshToken);

    /** 회전 계보 하나를 통째로 폐기하고, 실제로 폐기된 토큰 수를 돌려준다. */
    int revokeSession(UUID sessionId, Instant revokedAt);

    /** 한 소유자의 모든 기기 세션을 폐기하고, 실제로 폐기된 토큰 수를 돌려준다. */
    int revokeAllByOwnerId(UUID ownerId, Instant revokedAt);

    /**
     * 더는 쓰일 일이 없는 토큰을 지운다. 절대 만료가 지난 토큰과 폐기된 지 충분히 오래된 토큰이
     * 대상이며, 폐기 직후의 토큰은 재사용 탐지에 필요하므로 남긴다.
     */
    int deleteUnusableBefore(Instant now, Instant revokedThreshold);
}
