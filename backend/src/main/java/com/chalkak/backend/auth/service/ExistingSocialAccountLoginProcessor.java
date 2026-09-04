package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ID Token 검증을 마친 뒤의 기존 회원 로그인 처리를 제공자와 무관하게 담당한다. 소셜 계정 조회부터
 * 탈퇴·차단 정책 적용, Chalkak 토큰 발급까지가 제공자마다 같아 한곳에 모았다.
 *
 * <p>트랜잭션을 이 지점에서 여는 것은 ID Token 검증과 Apple 인가 코드 교환 같은 외부 호출을 트랜잭션
 * 밖에 두기 위해서다. 상위 서비스가 로그인 전체를 트랜잭션으로 감싸면 외부 응답을 기다리는 동안 DB
 * 커넥션을 점유하게 된다. 로그인 성공은 리프레시 토큰 계보를 새로 남기므로 읽기 전용일 수 없다.
 */
@Component
@RequiredArgsConstructor
public class ExistingSocialAccountLoginProcessor {

    private final SocialIdentityFingerprintEncoder fingerprintEncoder;
    private final SocialAccountRepository socialAccountRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final UserRefreshTokenService userRefreshTokenService;

    /**
     * 연결된 기존 회원이 있으면 로그인시키고, 없으면 빈 값을 돌려준다. 소셜 계정이 아예 없는 경우와 일반
     * 탈퇴 회원의 남은 연결을 만난 경우를 모두 빈 값으로 표현한다. 둘 다 상위 서비스에서 회원가입 흐름으로
     * 이어지므로 구분할 이유가 없다.
     */
    @Transactional
    public Optional<SocialLoginSuccess> processIfExists(VerifiedSocialIdentity identity) {
        String subjectHmac = fingerprintEncoder.encode(
                identity.provider(),
                identity.subject());

        return socialAccountRepository.findByProviderAndSubjectHmac(
                        identity.provider(),
                        subjectHmac)
                .map(SocialAccount::getUser)
                .flatMap(this::loginIfNotWithdrawn);
    }

    private Optional<SocialLoginSuccess> loginIfNotWithdrawn(User user) {
        validateNotWithdrawnBannedAccount(user);
        // 일반 회원은 탈퇴 시 연결을 삭제하지만, 이전 데이터에 남은 연결은 회원가입 필요로 처리한다.
        if (user.isDeleted()) {
            return Optional.empty();
        }
        UUID userId = user.getId();
        return Optional.of(new SocialLoginSuccess(
                userId,
                accessTokenIssuer.issue(userId),
                userRefreshTokenService.issue(user)));
    }

    /**
     * 차단 회원은 탈퇴해도 재가입 우회 방지를 위해 소셜 계정을 유지하므로 여기서 로그인을 거부한다.
     * 탈퇴하지 않은 차단 회원은 로그인은 되고, 쓰기 요청만 인가 단계에서 막힌다.
     */
    private void validateNotWithdrawnBannedAccount(User user) {
        if (user.isDeleted() && user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException(
                    ErrorCode.FORBIDDEN,
                    "탈퇴한 차단 소셜 계정입니다.");
        }
    }
}
