package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.GeneratedRefreshToken;
import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.domain.RefreshToken;
import com.chalkak.backend.auth.domain.RefreshTokenPolicy;
import com.chalkak.backend.auth.repository.RefreshTokenRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰의 발급과 회전 절차를 소유자 종류와 무관하게 수행한다.
 *
 * <p>회원과 관리자는 각자 실제 FK를 유지하려고 테이블과 엔티티를 나눴지만 회전과 폐기 절차는
 * 같다. 그래서 소유자 종류에 따라 달라지는 부분만 하위 클래스로 내리고 나머지는 여기 모은다.
 * 재사용 판정과 계보 폐기의 순서가 두 벌로 갈라지면 한쪽만 조용히 어긋나 탈취에 뚫리기 때문이다.
 *
 * @param <O> 토큰 소유자
 * @param <T> 소유자별 리프레시 토큰 엔티티
 */
public abstract class RefreshTokenService<O, T extends RefreshToken> {

    private static final String REAUTHENTICATION_REQUIRED_MESSAGE = "다시 로그인해 주세요.";

    private final RefreshTokenRepository<T> refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenPolicy refreshTokenPolicy;
    private final Clock clock;

    protected RefreshTokenService(
            RefreshTokenRepository<T> refreshTokenRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHasher refreshTokenHasher,
            RefreshTokenPolicy refreshTokenPolicy,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenHasher = refreshTokenHasher;
        this.refreshTokenPolicy = refreshTokenPolicy;
        this.clock = clock;
    }

    /**
     * 새 회전 계보를 시작한다. 기기마다 계보를 나눠야 한 기기의 탈취가 다른 기기의 로그인을 끊지
     * 않으므로 세션 식별자를 새로 만든다. 평문은 이 반환값으로 한 번만 나가고 저장소에는 해시만 남는다.
     */
    @Transactional
    public IssuedRefreshToken issue(O owner) {
        Instant now = clock.instant();
        Instant absoluteExpiresAt = refreshTokenPolicy.absoluteExpiresAt(now);
        Instant expiresAt = refreshTokenPolicy.nextExpiresAt(now, absoluteExpiresAt);
        GeneratedRefreshToken generated = refreshTokenGenerator.generateToken();
        refreshTokenRepository.save(createToken(
                owner,
                UUID.randomUUID(),
                generated.tokenHash(),
                expiresAt,
                absoluteExpiresAt));
        return toIssuedRefreshToken(generated, now, expiresAt);
    }

    /**
     * 제시된 토큰을 소비해 다음 리프레시 토큰과 액세스 토큰을 함께 내준다.
     *
     * <p>재사용과 만료로 계보를 폐기한 뒤에는 예외를 던지는데, 기본 롤백 규칙을 그대로 두면 방금 한
     * 폐기까지 되돌아가 탈취된 계보가 살아남는다. 그래서 이 예외만 롤백 대상에서 뺀다.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenRefreshResult refresh(String presentedToken) {
        Instant now = clock.instant();
        T consumed = refreshTokenRepository
                .findByTokenHashForUpdate(refreshTokenHasher.encode(presentedToken))
                .orElseThrow(RefreshTokenService::reauthenticationRequired);
        if (consumed.isRevoked()) {
            throw reauthenticationRequired();
        }
        if (consumed.isReused()) {
            refreshTokenRepository.revokeSession(consumed.getSessionId(), now);
            throw reauthenticationRequired();
        }
        if (consumed.isExpired(now)) {
            refreshTokenRepository.revokeSession(consumed.getSessionId(), now);
            throw reauthenticationRequired();
        }
        return rotate(consumed, now);
    }

    /**
     * 한 기기의 로그아웃. 모르는 토큰이나 이미 폐기된 토큰에도 예외를 던지지 않는다. 실패를 알리면
     * 토큰의 존재 여부가 새어 나가고, 재시도한 클라이언트가 로그아웃하지 못하고 막히기 때문이다.
     */
    @Transactional
    public void logout(String presentedToken) {
        refreshTokenRepository
                .findByTokenHashForUpdate(refreshTokenHasher.encode(presentedToken))
                .filter(refreshToken -> !refreshToken.isRevoked())
                .ifPresent(refreshToken -> refreshTokenRepository.revokeSession(
                        refreshToken.getSessionId(),
                        clock.instant()));
    }

    /** 한 소유자의 모든 기기 세션을 끊는다. */
    @Transactional
    public void revokeAll(UUID ownerId) {
        refreshTokenRepository.revokeAllByOwnerId(ownerId, clock.instant());
    }

    /** 계보의 첫 토큰을 만든다. 소유자 종류를 아는 것은 하위 클래스뿐이다. */
    protected abstract T createToken(
            O owner,
            UUID sessionId,
            String tokenHash,
            Instant expiresAt,
            Instant absoluteExpiresAt
    );

    /** 회전으로 뒤를 잇는 토큰을 만든다. 반환 타입이 소유자별로 갈리므로 하위 클래스가 잇는다. */
    protected abstract T createSuccessor(
            T consumed,
            String tokenHash,
            Instant expiresAt
    );

    /** 회전과 함께 내려보낼 액세스 토큰을 발급한다. 소유자마다 scope가 다르다. */
    protected abstract IssuedAccessToken issueAccessToken(T consumed);

    /**
     * 회전으로 다음 토큰을 만든다.
     *
     * <p>잠금이 같은 토큰의 동시 재발급을 줄 세우므로, 먼저 커밋한 요청의 회전 기록을 뒤이은 요청이
     * 보게 된다. 뒤이은 요청은 회전된 토큰을 제시한 셈이라 재사용으로 걸러지고 여기까지 내려오지
     * 않는다. 한 계보에 살아 있는 토큰은 항상 하나뿐이다.
     */
    private TokenRefreshResult rotate(T consumed, Instant now) {
        GeneratedRefreshToken generated = refreshTokenGenerator.generateToken();
        Instant expiresAt = refreshTokenPolicy.nextExpiresAt(
                now,
                consumed.getAbsoluteExpiresAt());
        consumed.rotate(now);
        refreshTokenRepository.save(createSuccessor(
                consumed,
                generated.tokenHash(),
                expiresAt));
        return new TokenRefreshResult(
                issueAccessToken(consumed),
                toIssuedRefreshToken(generated, now, expiresAt));
    }

    private IssuedRefreshToken toIssuedRefreshToken(
            GeneratedRefreshToken generated,
            Instant now,
            Instant expiresAt
    ) {
        return new IssuedRefreshToken(
                generated.value(),
                Duration.between(now, expiresAt));
    }

    /** 실패 원인을 구분해 알리면 토큰 대입 공격에 힌트를 주므로, 모든 거절을 같은 응답으로 묶는다. */
    private static UnauthorizedException reauthenticationRequired() {
        return new UnauthorizedException(
                ErrorCode.REAUTHENTICATION_REQUIRED,
                REAUTHENTICATION_REQUIRED_MESSAGE);
    }
}
