package com.chalkak.backend.auth.repository;

import com.chalkak.backend.auth.domain.BannedSocialIdentity;
import com.chalkak.backend.auth.domain.SocialProvider;

public interface BannedSocialIdentityRepository {

    BannedSocialIdentity save(BannedSocialIdentity bannedSocialIdentity);

    boolean existsByProviderAndSubjectHmac(
            SocialProvider provider,
            String subjectHmac);

    void deleteByProviderAndSubjectHmac(
            SocialProvider provider,
            String subjectHmac);
}
