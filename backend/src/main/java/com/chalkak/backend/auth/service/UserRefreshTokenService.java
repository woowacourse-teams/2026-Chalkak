package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.GeneratedRefreshToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.domain.RefreshTokenPolicy;
import com.chalkak.backend.auth.domain.UserRefreshToken;
import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 리프레시 토큰의 발급과 회전을 담당한다.
 *
 * <p>여기서는 회원 상태를 조회하지 않는다. 탈퇴는 탈퇴 처리에서 계보를 직접 폐기하고 차단은
 * 요청마다 {@code UsableUserPolicy}가 막으므로, 재발급 경로에 회원 조회를 추가하면 같은 판정을
 * 두 곳에서 하게 된다.
 */
@Service
@RequiredArgsConstructor
public class UserRefreshTokenService {

    private static final String REAUTHENTICATION_REQUIRED_MESSAGE = "다시 로그인해 주세요.";

    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenPolicy refreshTokenPolicy;
    private final AccessTokenIssuer accessTokenIssuer;
    private final Clock clock;

    /**
     * 새 회전 계보를 시작한다. 기기마다 계보를 나눠야 한 기기의 탈취가 다른 기기의 로그인을 끊지
     * 않으므로 세션 식별자를 새로 만든다. 평문은 이 반환값으로 한 번만 나가고 저장소에는 해시만 남는다.
     */
    @Transactional
    public IssuedRefreshToken issue(User user) {
        Instant now = clock.instant();
        Instant absoluteExpiresAt = refreshTokenPolicy.absoluteExpiresAt(now);
        Instant expiresAt = refreshTokenPolicy.nextExpiresAt(now, absoluteExpiresAt);
        GeneratedRefreshToken generated = refreshTokenGenerator.generateToken();
        userRefreshTokenRepository.save(UserRefreshToken.create(
                user,
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
        UserRefreshToken consumed = userRefreshTokenRepository
                .findByTokenHashForUpdate(refreshTokenHasher.encode(presentedToken))
                .orElseThrow(UserRefreshTokenService::reauthenticationRequired);
        if (consumed.isRevoked()) {
            throw reauthenticationRequired();
        }
        if (consumed.isReused(now, refreshTokenPolicy.reuseGrace())) {
            userRefreshTokenRepository.revokeSession(consumed.getSessionId(), now);
            throw reauthenticationRequired();
        }
        if (consumed.isExpired(now)) {
            userRefreshTokenRepository.revokeSession(consumed.getSessionId(), now);
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
        userRefreshTokenRepository
                .findByTokenHashForUpdate(refreshTokenHasher.encode(presentedToken))
                .filter(refreshToken -> !refreshToken.isRevoked())
                .ifPresent(refreshToken -> userRefreshTokenRepository.revokeSession(
                        refreshToken.getSessionId(),
                        clock.instant()));
    }

    /** 한 회원의 모든 기기 세션을 끊는다. */
    @Transactional
    public void revokeAll(UUID userId) {
        userRefreshTokenRepository.revokeAllByOwnerId(userId, clock.instant());
    }

    /**
     * 회전으로 다음 토큰을 만든다.
     *
     * <p>행 잠금이 같은 토큰의 동시 재발급을 줄 세우므로, 먼저 커밋한 요청의 회전 기록을 뒤이은
     * 요청이 보게 된다. 이때 유예 시간 안이면 재사용이 아니라 재시도로 보고 여기까지 내려와 각자
     * 자기 후속 토큰을 받는다. 한 계보에 잠시 두 토큰이 함께 사는 셈이지만, 401 뒤에 큐에 쌓인
     * 요청을 한꺼번에 보내는 정상 클라이언트의 세션을 끊지 않기 위한 의도된 상태이며 다음 회전에서
     * 하나로 수렴한다.
     */
    private TokenRefreshResult rotate(UserRefreshToken consumed, Instant now) {
        GeneratedRefreshToken generated = refreshTokenGenerator.generateToken();
        Instant expiresAt = refreshTokenPolicy.nextExpiresAt(
                now,
                consumed.getAbsoluteExpiresAt());
        consumed.rotate(now);
        userRefreshTokenRepository.save(consumed.createSuccessor(
                generated.tokenHash(),
                expiresAt));
        return new TokenRefreshResult(
                accessTokenIssuer.issue(consumed.getUser().getId()),
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
