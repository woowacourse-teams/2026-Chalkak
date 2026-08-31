package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountJpaRepository extends JpaRepository<SocialAccount, UUID> {

    @Query("""
            SELECT socialAccount
            FROM SocialAccount socialAccount
            JOIN FETCH socialAccount.user
            WHERE socialAccount.provider = :provider
              AND socialAccount.subjectHmac = :subjectHmac
            """)
    Optional<SocialAccount> findByProviderAndSubjectHmac(
            @Param("provider") SocialProvider provider,
            @Param("subjectHmac") String subjectHmac);

    Optional<SocialAccount> findByUserId(UUID userId);

    List<SocialAccount> findAllBySubjectHmacIsNull();

    void deleteByUserId(UUID userId);
}
