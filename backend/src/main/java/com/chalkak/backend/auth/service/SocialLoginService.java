package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialIdentityVerifier socialIdentityVerifier;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialIdentityFingerprintEncoder fingerprintEncoder;
    private final AccessTokenIssuer accessTokenIssuer;

    @Transactional(readOnly = true)
    public SocialLoginResult login(
            SocialProvider provider,
            String idToken
    ) {
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                provider,
                idToken);
        String subjectHmac = fingerprintEncoder.encode(
                identity.provider(),
                identity.subject());

        return socialAccountRepository.findByProviderAndSubjectHmac(
                        identity.provider(),
                        subjectHmac)
                .map(this::toLoginResult)
                .orElseGet(SocialLoginResult::signUpRequired);
    }

    private SocialLoginResult toLoginResult(SocialAccount socialAccount) {
        User user = socialAccount.getUser();
        // 차단 회원은 탈퇴해도 재가입 우회 방지를 위해 소셜 계정을 유지하므로 여기서 로그인을 거부한다.
        if (user.isDeleted() && user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException(
                    ErrorCode.FORBIDDEN,
                    "탈퇴한 차단 소셜 계정입니다.");
        }
        // 일반 회원은 탈퇴 시 연결을 삭제하지만, 이전 데이터에 남은 연결은 회원가입 필요로 처리한다.
        if (user.isDeleted()) {
            return SocialLoginResult.signUpRequired();
        }
        UUID userId = user.getId();
        return SocialLoginResult.loginSuccess(
                userId,
                accessTokenIssuer.issue(userId));
    }
}
