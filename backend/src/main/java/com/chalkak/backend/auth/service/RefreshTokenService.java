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
import java.util.List;
import java.util.Optional;
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
        T consumed = lockLineageAndRead(refreshTokenHasher.encode(presentedToken))
                .orElseThrow(RefreshTokenService::reauthenticationRequired);
        Instant now = clock.instant();
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
        lockLineageAndRead(refreshTokenHasher.encode(presentedToken))
                .filter(refreshToken -> !refreshToken.isRevoked())
                .ifPresent(refreshToken -> refreshTokenRepository.revokeSession(
                        refreshToken.getSessionId(),
                        clock.instant()));
    }

    /**
     * 한 소유자의 모든 기기 세션을 끊는다.
     *
     * <p>한 번의 UPDATE로 모두 끊으니 안전해 보이지만, 갱신 단위가 계보 하나가 아닐 뿐 잃어버린
     * 갱신은 똑같이 일어난다. UPDATE는 문장이 시작할 때 보이던 행만 훑으므로, 그 사이에 회전이
     * 커밋한 후속 토큰은 폐기를 빠져나가 탈퇴한 회원의 기기 하나가 살아남는다. 그래서 회전과 같은
     * 계보 잠금을 먼저 잡는다.
     *
     * <p>잠금은 이미 존재하는 계보만 덮는다. 회전은 계보를 새로 만들지 않으므로 이것으로 충분하고,
     * 계보를 새로 만드는 것은 로그인뿐인데 탈퇴 트랜잭션이 회원 상태와 소셜 연결을 함께 정리하므로
     * 그 뒤의 로그인은 같은 소유자로 이어지지 않는다.
     */
    @Transactional
    public void revokeAll(UUID ownerId) {
        List<UUID> sessionIds = refreshTokenRepository.findLiveSessionIdsByOwnerId(ownerId);
        sessionIds.forEach(refreshTokenRepository::lockSession);
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

    /**
     * 토큰이 속한 계보를 잠근 뒤, 그 잠금 아래에서 다시 읽은 토큰을 돌려준다.
     *
     * <p>잠금 단위를 행이 아니라 계보로 잡는 이유는 바꾸는 단위가 계보이기 때문이다. 회전과 폐기는
     * 모두 {@code session_id} 하나를 통째로 건드리는데 잠금이 행 하나뿐이면, READ COMMITTED에서
     * 계보 폐기 UPDATE가 문장 시작 시점에 보이던 행만 훑어 그 사이 커밋된 후속 토큰이 폐기를
     * 빠져나간다. 하필 폐기가 가장 필요한 순간, 즉 피해자가 회전하는 사이 공격자가 옛 토큰을 내미는
     * 순간에 그렇게 된다. 행 잠금 두 개를 서로 반대 순서로 잡는 회전과 로그아웃이 맞물려 교착에
     * 빠지는 문제도 같은 뿌리에서 나온다.
     *
     * <p>그래서 어떤 행에도 손대기 전에 계보 잠금부터 잡아야 한다. 행을 먼저 잠그고 계보를 나중에
     * 잠그면 잠금 순서가 요청마다 갈려 교착이 그대로 돌아온다. lineage 식별자를 읽는 첫 조회는
     * 잠금 없이 하므로 곧 낡을 수 있고, 판단은 모두 잠금을 얻은 뒤의 두 번째 조회로 한다.
     */
    private Optional<T> lockLineageAndRead(String tokenHash) {
        Optional<UUID> sessionId = refreshTokenRepository.findSessionIdByTokenHash(tokenHash);
        if (sessionId.isEmpty()) {
            return Optional.empty();
        }
        refreshTokenRepository.lockSession(sessionId.get());
        return refreshTokenRepository.findByTokenHash(tokenHash);
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
