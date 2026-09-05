package com.chalkak.backend.auth.repository;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import java.util.Optional;
import java.util.UUID;

public interface SocialAccountRepository {

    Optional<SocialAccount> findByProviderAndSubjectHmac(
            SocialProvider provider,
            String subjectHmac);

    Optional<SocialAccount> findByUserId(UUID userId);

    SocialAccount save(SocialAccount socialAccount);

    void deleteByUserId(UUID userId);
}
