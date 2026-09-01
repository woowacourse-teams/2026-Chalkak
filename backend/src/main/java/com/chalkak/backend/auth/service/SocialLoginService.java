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
        if (user.isDeleted() && user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException(
                    ErrorCode.FORBIDDEN,
                    "차단된 소셜 계정입니다.");
        }
        if (user.isDeleted()) {
            return SocialLoginResult.signUpRequired();
        }
        UUID userId = user.getId();
        return SocialLoginResult.loginSuccess(
                userId,
                accessTokenIssuer.issue(userId));
    }
}
