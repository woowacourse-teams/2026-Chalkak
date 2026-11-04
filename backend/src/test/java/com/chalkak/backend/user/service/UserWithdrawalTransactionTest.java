package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.identity.SocialAccount;
import com.chalkak.backend.auth.domain.identity.SocialProvider;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.auth.service.identity.SocialIdentityFingerprintEncoder;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.support.AuthorPostFixtures;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class UserWithdrawalTransactionTest extends IntegrationTestSupport {

    @Autowired
    private UserWithdrawalService userWithdrawalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private SocialIdentityFingerprintEncoder fingerprintEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner = new DatabaseCleaner(jdbcTemplate);
        databaseCleaner.clean();
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("공개 탈퇴 진입점은 내부 완료 메서드의 트랜잭션으로 회원을 탈퇴 처리한다")
    void withdraw_activeUser_completesWithdrawalInTransaction() {
        // Given
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                fingerprintEncoder.encode(
                        SocialProvider.GOOGLE,
                        "google-subject")
        ));

        // When
        userWithdrawalService.withdraw(user.getId());

        // Then
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("탈퇴 트랜잭션이 롤백되면 게시물의 인스타그램 동의 철회도 함께 롤백된다")
    void completeWithdrawal_rolledBack_restoresInstagramConsent() {
        // Given
        User user = userRepository.save(UserFixture.create());
        UUID postId = AuthorPostFixtures.createPost(
                jdbcTemplate,
                user.getId(),
                LocalDate.parse("2026-11-01"),
                ModerationStatus.APPROVED
        );
        jdbcTemplate.update(
                """
                        UPDATE posts
                        SET instagram_upload_agreed = TRUE, instagram_id = 'chalkak',
                            instagram_agreed_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """,
                postId
        );

        // When
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            userService.completeWithdrawal(user.getId(), List.of());
            entityManager.flush();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT instagram_upload_agreed FROM posts WHERE id = ?",
                    Boolean.class,
                    postId
            )).isFalse();
            status.setRollbackOnly();
        });

        // Then
        assertThat(userRepository.findActiveById(user.getId())).isPresent();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT instagram_upload_agreed, instagram_id, instagram_revoked_at FROM posts WHERE id = ?",
                postId
        )).containsEntry("instagram_upload_agreed", true)
                .containsEntry("instagram_id", "chalkak")
                .containsEntry("instagram_revoked_at", null);
    }
}
