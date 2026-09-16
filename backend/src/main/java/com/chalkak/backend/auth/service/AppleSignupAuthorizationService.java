package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.domain.VerifiedSocialSignupToken;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppleSignupAuthorizationService {

    /**
     * 가입이 끝나지 않은 Apple Refresh Token의 임시 보관 기간. 클라이언트는 로그인에 쓴 ID
     * Token으로 업로드 URL을 다시 요청하므로, Apple ID Token 수명 10분 안에 업로드 URL을 받고
     * 그 뒤 회원가입 토큰(5분) 안에 가입을 끝낼 시간을 덮는다.
     */
    private static final Duration PENDING_AUTHORIZATION_EXPIRATION =
            Duration.ofMinutes(15);

    private final AppleAuthorizationRepository appleAuthorizationRepository;
    private final PendingAppleAuthorizationRepository pendingAuthorizationRepository;
    private final SocialIdentityFingerprintEncoder fingerprintEncoder;
    private final Clock clock;

    /**
     * 만료 전 보관분이 있으면 만료만 다시 밀고 재사용했음을 알린다. 재로그인마다 새로
     * 교환하면 Apple에 grant가 하나씩 늘어나는데, 이미 보관한 Refresh Token으로 탈퇴 시
     * 폐기가 가능하므로 다시 받을 이유가 없다.
     *
     * <p>행을 잠그고 읽는 것은 만료를 미는 경로가 로그인과 업로드 URL 발급 둘이기 때문이다.
     * 같은 신원의 두 요청이 잠금 없이 같은 값을 읽으면 나중에 쓴 쪽이 이겨,
     * {@link PendingAppleAuthorization#extendTo}가 약속한 "앞으로만 간다"가 깨진다.
     */
    @Transactional
    public boolean renewIfPresent(VerifiedSocialIdentity identity) {
        Optional<PendingAppleAuthorization> pendingAuthorization =
                pendingAuthorizationRepository
                        .findLatestUnexpiredBySubjectHmacForUpdate(
                                subjectHmac(identity),
                                clock.instant());
        if (pendingAuthorization.isEmpty()) {
            return false;
        }
        pendingAuthorization.get().extendTo(expiresAt());
        return true;
    }

    public void store(
            VerifiedSocialIdentity identity,
            String encryptedRefreshToken
    ) {
        pendingAuthorizationRepository.save(PendingAppleAuthorization.create(
                subjectHmac(identity),
                encryptedRefreshToken,
                expiresAt()));
    }

    /**
     * Apple 신규 회원은 로그인 때 보관한 Refresh Token이 있어야 가입을 끝낼 수 있으므로,
     * 보관분이 없거나 만료됐으면 업로드 URL을 발급하지 않는다. 남아 있으면 회원가입 토큰이
     * 살아 있는 동안 보관분이 먼저 만료되지 않도록 만료를 맞춘다.
     */
    @Transactional
    public void extendIfApple(VerifiedSocialIdentity identity, Instant expiresAt) {
        if (identity.provider() != SocialProvider.APPLE) {
            return;
        }
        PendingAppleAuthorization pendingAuthorization =
                getPendingAuthorizationForUpdate(subjectHmac(identity));
        pendingAuthorization.extendTo(expiresAt);
    }

    /**
     * 한 신원에 여러 임시 인증 정보가 남아 있어도 최신 하나만 정식 보관으로 옮긴다. 나머지는
     * 이미 교환된 Refresh Token이라 삭제하면 Apple에 폐기할 수 없는 grant가 남으므로,
     * 정리 스케줄러가 만료 후 폐기하도록 그대로 둔다.
     */
    public void saveIfApple(
            VerifiedSocialSignupToken verifiedToken,
            SocialAccount socialAccount
    ) {
        if (verifiedToken.provider() != SocialProvider.APPLE) {
            return;
        }
        PendingAppleAuthorization pendingAuthorization =
                getPendingAuthorizationForUpdate(fingerprintEncoder.encode(
                        verifiedToken.provider(),
                        verifiedToken.subject()));
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                pendingAuthorization.getEncryptedRefreshToken()));
        pendingAuthorizationRepository.delete(pendingAuthorization);
    }

    private String subjectHmac(VerifiedSocialIdentity identity) {
        return fingerprintEncoder.encode(identity.provider(), identity.subject());
    }

    private Instant expiresAt() {
        return clock.instant().plus(PENDING_AUTHORIZATION_EXPIRATION);
    }

    private PendingAppleAuthorization getPendingAuthorizationForUpdate(
            String subjectHmac
    ) {
        return pendingAuthorizationRepository
                .findLatestUnexpiredBySubjectHmacForUpdate(
                        subjectHmac,
                        clock.instant())
                .orElseThrow(this::pendingAuthorizationNotFound);
    }

    private BusinessException pendingAuthorizationNotFound() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "Apple 회원가입 인증 정보가 만료되었거나 없습니다. 다시 로그인해 주세요.");
    }
}
