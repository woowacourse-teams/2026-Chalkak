package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.auth.domain.BannedSocialIdentity;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.infrastructure.infra.restriction.HmacSocialIdentityFingerprintEncoder;
import com.chalkak.backend.auth.repository.BannedSocialIdentityRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SocialIdentityRestrictionServiceTest extends IntegrationTestSupport {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String SUBJECT = "google-subject";
    private static final String SUBJECT_HMAC =
            "921c5d35312df654eaa8ec114fd1de5a156cbcc64b23ddb6a709a9423f90c218";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private BannedSocialIdentityRepository bannedSocialIdentityRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private SocialIdentityRestrictionService socialIdentityRestrictionService;

    @BeforeEach
    void setUp() {
        socialIdentityRestrictionService = new SocialIdentityRestrictionService(
                socialAccountRepository,
                bannedSocialIdentityRepository,
                new HmacSocialIdentityFingerprintEncoder(SECRET));
    }

    @Test
    @DisplayName("사용자를 차단하면 소셜 계정 식별자의 HMAC을 차단 목록에 저장한다")
    void block_existingSocialAccount_savesBannedIdentity() {
        // Given
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                SUBJECT));
        flushAndClear();

        // When
        socialIdentityRestrictionService.block(user.getId());
        flushAndClear();

        // Then
        assertThat(bannedSocialIdentityRepository.existsByProviderAndSubjectHmac(
                SocialProvider.GOOGLE,
                SUBJECT_HMAC))
                .isTrue();
    }

    @Test
    @DisplayName("사용자의 차단을 해제하면 소셜 계정 식별자를 차단 목록에서 삭제한다")
    void unblock_bannedSocialIdentity_deletesBannedIdentity() {
        // Given
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                SUBJECT));
        bannedSocialIdentityRepository.save(BannedSocialIdentity.create(
                SocialProvider.GOOGLE,
                SUBJECT_HMAC));
        flushAndClear();

        // When
        socialIdentityRestrictionService.unblock(user.getId());
        flushAndClear();

        // Then
        assertThat(bannedSocialIdentityRepository.existsByProviderAndSubjectHmac(
                SocialProvider.GOOGLE,
                SUBJECT_HMAC))
                .isFalse();
    }

    @Test
    @DisplayName("차단 목록에 등록된 소셜 계정은 사용할 수 없다")
    void validateNotBlocked_bannedSocialIdentity_throwsForbiddenException() {
        // Given
        bannedSocialIdentityRepository.save(BannedSocialIdentity.create(
                SocialProvider.GOOGLE,
                SUBJECT_HMAC));
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> socialIdentityRestrictionService.validateNotBlocked(
                SocialProvider.GOOGLE,
                SUBJECT))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("차단된 소셜 계정입니다.");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
