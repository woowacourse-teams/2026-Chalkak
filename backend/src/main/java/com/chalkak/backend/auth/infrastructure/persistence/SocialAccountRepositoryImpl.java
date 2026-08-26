package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SocialAccountRepositoryImpl implements SocialAccountRepository {

    private final SocialAccountJpaRepository socialAccountJpaRepository;

    @Override
    public Optional<SocialAccount> findByProviderAndSubject(
            SocialProvider provider,
            String subject) {
        return socialAccountJpaRepository.findByProviderAndSubject(provider, subject);
    }

    @Override
    public SocialAccount save(SocialAccount socialAccount) {
        return socialAccountJpaRepository.save(socialAccount);
    }
}
