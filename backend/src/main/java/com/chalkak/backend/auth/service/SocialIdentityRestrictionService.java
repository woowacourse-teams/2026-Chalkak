package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.BannedSocialIdentity;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.BannedSocialIdentityRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialIdentityRestrictionService {

    private final SocialAccountRepository socialAccountRepository;
    private final BannedSocialIdentityRepository bannedSocialIdentityRepository;
    private final SocialIdentityFingerprintEncoder fingerprintEncoder;

    @Transactional
    public void block(UUID userId) {
        SocialAccount socialAccount = socialAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "차단할 소셜 계정을 찾을 수 없습니다."));
        String subjectHmac = fingerprintEncoder.encode(
                socialAccount.getProvider(),
                socialAccount.getSubject());
        bannedSocialIdentityRepository.save(BannedSocialIdentity.create(
                socialAccount.getProvider(),
                subjectHmac));
    }

    @Transactional
    public void unblock(UUID userId) {
        SocialAccount socialAccount = socialAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "차단 해제할 소셜 계정을 찾을 수 없습니다."));
        String subjectHmac = fingerprintEncoder.encode(
                socialAccount.getProvider(),
                socialAccount.getSubject());
        bannedSocialIdentityRepository.deleteByProviderAndSubjectHmac(
                socialAccount.getProvider(),
                subjectHmac);
    }

    @Transactional(readOnly = true)
    public void validateNotBlocked(
            SocialProvider provider,
            String subject
    ) {
        String subjectHmac = fingerprintEncoder.encode(provider, subject);
        if (bannedSocialIdentityRepository.existsByProviderAndSubjectHmac(
                provider,
                subjectHmac)) {
            throw new ForbiddenException(
                    ErrorCode.FORBIDDEN,
                    "차단된 소셜 계정입니다.");
        }
    }
}
