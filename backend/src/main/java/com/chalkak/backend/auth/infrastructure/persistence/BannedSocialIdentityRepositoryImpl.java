package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.BannedSocialIdentity;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.BannedSocialIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BannedSocialIdentityRepositoryImpl implements
        BannedSocialIdentityRepository {

    private final BannedSocialIdentityJpaRepository bannedSocialIdentityJpaRepository;

    @Override
    public BannedSocialIdentity save(BannedSocialIdentity bannedSocialIdentity) {
        return bannedSocialIdentityJpaRepository.save(bannedSocialIdentity);
    }

    @Override
    public boolean existsByProviderAndSubjectHmac(
            SocialProvider provider,
            String subjectHmac
    ) {
        return bannedSocialIdentityJpaRepository
                .existsByProviderAndSubjectHmac(provider, subjectHmac);
    }

    @Override
    public void deleteByProviderAndSubjectHmac(
            SocialProvider provider,
            String subjectHmac
    ) {
        bannedSocialIdentityJpaRepository
                .deleteByProviderAndSubjectHmac(provider, subjectHmac);
    }
}
