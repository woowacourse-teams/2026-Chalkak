package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT socialAccount
            FROM SocialAccount socialAccount
            JOIN FETCH socialAccount.user
            WHERE socialAccount.provider = :provider
              AND socialAccount.subjectHmac = :subjectHmac
            """)
    Optional<SocialAccount> findByProviderAndSubjectHmacForUpdate(
            @Param("provider") SocialProvider provider,
            @Param("subjectHmac") String subjectHmac);

    Optional<SocialAccount> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
