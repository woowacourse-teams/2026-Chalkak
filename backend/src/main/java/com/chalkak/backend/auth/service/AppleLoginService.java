package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.AppleSignupAuthorization;
import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserStatus;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppleLoginService {

    private final AppleIdTokenVerifier appleIdTokenVerifier;
    private final AppleTokenClient appleTokenClient;
    private final AppleRefreshTokenCipher refreshTokenCipher;
    private final SocialIdentityFingerprintEncoder fingerprintEncoder;
    private final SocialAccountRepository socialAccountRepository;
    private final AppleAuthorizationService appleAuthorizationService;
    private final SocialSignupTokenIssuer socialSignupTokenIssuer;
    private final AccessTokenIssuer accessTokenIssuer;

    public AppleLoginResult login(
            String idToken,
            String authorizationCode,
            String rawNonce
    ) {
        VerifiedSocialIdentity identity = appleIdTokenVerifier.verify(
                idToken,
                rawNonce);
        String subjectHmac = fingerprintEncoder.encode(
                SocialProvider.APPLE,
                identity.subject());
        boolean hasExistingAccount = validateExistingAccount(subjectHmac);

        AppleTokenExchangeResult exchangeResult = appleTokenClient
                .exchangeAuthorizationCode(authorizationCode);
        VerifiedSocialIdentity exchangedIdentity = appleIdTokenVerifier.verify(
                exchangeResult.idToken(),
                rawNonce);
        validateSameSubject(identity, exchangedIdentity);

        String encryptedRefreshToken = refreshTokenCipher.encrypt(
                exchangeResult.refreshToken());
        AppleSignupAuthorization signupAuthorization =
                new AppleSignupAuthorization(
                        exchangeResult.clientId(),
                        encryptedRefreshToken);
        if (!hasExistingAccount) {
            return issueSignupToken(identity, signupAuthorization);
        }

        Optional<UUID> userId = appleAuthorizationService.saveForExistingAccount(
                subjectHmac,
                signupAuthorization.clientId(),
                signupAuthorization.encryptedRefreshToken());
        if (userId.isEmpty()) {
            return issueSignupToken(identity, signupAuthorization);
        }
        return AppleLoginResult.loginSuccess(
                userId.get(),
                accessTokenIssuer.issue(userId.get()));
    }

    private boolean validateExistingAccount(String subjectHmac) {
        Optional<SocialAccount> socialAccount = socialAccountRepository
                .findByProviderAndSubjectHmac(
                        SocialProvider.APPLE,
                        subjectHmac);
        if (socialAccount.isEmpty()) {
            return false;
        }
        User user = socialAccount.get().getUser();
        if (user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException(
                    ErrorCode.FORBIDDEN,
                    "차단된 소셜 계정입니다.");
        }
        return !user.isDeleted();
    }

    private void validateSameSubject(
            VerifiedSocialIdentity identity,
            VerifiedSocialIdentity exchangedIdentity
    ) {
        if (!identity.subject().equals(exchangedIdentity.subject())) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "Apple 로그인 사용자 정보가 일치하지 않습니다.");
        }
    }

    private AppleLoginResult issueSignupToken(
            VerifiedSocialIdentity identity,
            AppleSignupAuthorization signupAuthorization
    ) {
        IssuedSocialSignupToken signupToken = socialSignupTokenIssuer.issueApple(
                identity,
                UUID.randomUUID(),
                signupAuthorization);
        return AppleLoginResult.signUpRequired(signupToken);
    }
}
