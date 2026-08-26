package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountJpaRepository extends JpaRepository<SocialAccount, UUID> {

    Optional<SocialAccount> findByProviderAndSubject(
            SocialProvider provider,
            String subject);
}
