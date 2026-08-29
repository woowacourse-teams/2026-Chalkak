package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialIdentityVerifier socialIdentityVerifier;
    private final SocialAccountRepository socialAccountRepository;
    private final AccessTokenIssuer accessTokenIssuer;

    @Transactional(readOnly = true)
    public SocialLoginResult login(
            SocialProvider provider,
            String idToken
    ) {
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                provider,
                idToken);

        return socialAccountRepository.findByProviderAndSubject(
                        identity.provider(),
                        identity.subject())
                .map(this::toLoginSuccess)
                .orElseGet(SocialLoginResult::signUpRequired);
    }

    private SocialLoginResult toLoginSuccess(SocialAccount socialAccount) {
        if (socialAccount.getUser().isDeleted()) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "탈퇴한 회원은 로그인할 수 없습니다.");
        }
        UUID userId = socialAccount.getUser().getId();
        return SocialLoginResult.loginSuccess(
                userId,
                accessTokenIssuer.issue(userId));
    }
}
