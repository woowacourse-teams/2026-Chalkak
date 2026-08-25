package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.repository.SignatureImageUploadIssuer;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialSignupService {

    private final SocialIdentityVerifier socialIdentityVerifier;
    private final SocialAccountRepository socialAccountRepository;
    private final SignatureImageUploadIssuer signatureImageUploadIssuer;

    public SignatureImageUpload createSignatureUpload(
            SocialProvider provider,
            String idToken
    ) {
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                provider,
                idToken);
        validateNewSocialAccount(identity);

        return signatureImageUploadIssuer.issue(UUID.randomUUID());
    }

    private void validateNewSocialAccount(VerifiedSocialIdentity identity) {
        boolean exists = socialAccountRepository.findByProviderAndSubject(
                        identity.provider(),
                        identity.subject())
                .isPresent();
        if (exists) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이미 가입된 소셜 계정입니다.");
        }
    }
}
