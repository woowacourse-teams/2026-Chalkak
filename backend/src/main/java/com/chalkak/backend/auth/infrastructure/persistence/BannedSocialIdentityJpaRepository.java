package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.BannedSocialIdentity;
import com.chalkak.backend.auth.domain.SocialProvider;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannedSocialIdentityJpaRepository extends
        JpaRepository<BannedSocialIdentity, UUID> {

    boolean existsByProviderAndSubjectHmac(
            SocialProvider provider,
            String subjectHmac);

    void deleteByProviderAndSubjectHmac(
            SocialProvider provider,
            String subjectHmac);
}
