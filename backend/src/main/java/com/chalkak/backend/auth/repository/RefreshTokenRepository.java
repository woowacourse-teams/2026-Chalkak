package com.chalkak.backend.auth.repository;

import com.chalkak.backend.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 회원과 관리자의 리프레시 토큰 저장소가 공유하는 포트. 두 테이블은 각자 실제 FK를 유지하려고
 * 나뉘어 있지만 회전과 폐기 절차는 같으므로, 회전 서비스가 소유자 종류와 무관하게 동작하도록 묶는다.
 */
public interface RefreshTokenRepository<T extends RefreshToken> {

    /**
     * 계보 잠금을 걸기 위해 토큰이 속한 lineage 식별자만 읽는다. 이 시점에는 아직 아무 잠금도 없으므로
     * 결과는 곧 낡을 수 있고, 판단에 쓰지 않고 잠금 대상을 고르는 데만 쓴다.
     */
    Optional<UUID> findSessionIdByTokenHash(String tokenHash);

    /** 한 소유자의 살아 있는 계보 식별자를 모두 읽는다. 계보 단위 잠금을 걸 대상을 고르는 데 쓴다. */
    List<UUID> findLiveSessionIdsByOwnerId(UUID ownerId);

    /**
     * 회전 계보 하나를 트랜잭션이 끝날 때까지 잠근다. 행이 아니라 lineage 자체를 잠그므로 아직
     * 존재하지 않는 후속 토큰까지 같은 잠금 아래로 들어온다.
     */
    void lockSession(UUID sessionId);

    /** 계보 잠금을 얻은 뒤 회전 대상 토큰을 다시 읽는다. 잠금을 기다리는 동안 상태가 바뀔 수 있다. */
    Optional<T> findByTokenHash(String tokenHash);

    T save(T refreshToken);

    /** 회전 계보 하나를 통째로 폐기하고, 실제로 폐기된 토큰 수를 돌려준다. */
    int revokeSession(UUID sessionId, Instant revokedAt);

    /** 한 소유자의 모든 기기 세션을 폐기하고, 실제로 폐기된 토큰 수를 돌려준다. */
    int revokeAllByOwnerId(UUID ownerId, Instant revokedAt);

    /**
     * 더는 쓰일 일이 없는 토큰을 지우고, 실제로 지운 토큰 수를 돌려준다. 절대 만료가 지난 토큰과
     * 폐기된 지 충분히 오래된 토큰이 대상이며, 폐기 직후의 토큰은 재사용 탐지에 필요하므로 남긴다.
     *
     * <p>두 조건은 서로 다른 컬럼을 보므로 구현은 문장을 나눠 실행한다. 호출자에게는 여전히 한 번의
     * 정리이고, 돌려주는 값도 두 조건을 합쳐 지운 총 개수다.
     */
    int deleteUnusableBefore(Instant now, Instant revokedThreshold);
}
