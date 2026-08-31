package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SocialAccountRepositoryImpl implements SocialAccountRepository {

    private final SocialAccountJpaRepository socialAccountJpaRepository;

    @Override
    public Optional<SocialAccount> findByProviderAndSubjectHmac(
            SocialProvider provider,
            String subjectHmac) {
        return socialAccountJpaRepository.findByProviderAndSubjectHmac(
                provider,
                subjectHmac);
    }

    @Override
    public Optional<SocialAccount> findByUserId(UUID userId) {
        return socialAccountJpaRepository.findByUserId(userId);
    }

    @Override
    public SocialAccount save(SocialAccount socialAccount) {
        return socialAccountJpaRepository.save(socialAccount);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        socialAccountJpaRepository.deleteByUserId(userId);
    }
}
