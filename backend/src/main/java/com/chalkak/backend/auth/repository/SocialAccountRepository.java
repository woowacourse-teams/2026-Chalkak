package com.chalkak.backend.auth.repository;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import java.util.Optional;

public interface SocialAccountRepository {

    Optional<SocialAccount> findByProviderAndSubject(
            SocialProvider provider,
            String subject);

    SocialAccount save(SocialAccount socialAccount);
}
