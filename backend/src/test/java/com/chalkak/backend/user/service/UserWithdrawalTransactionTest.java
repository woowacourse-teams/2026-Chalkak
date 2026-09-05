package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.auth.service.SocialIdentityFingerprintEncoder;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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
                        "google-subject")));

        // When
        userWithdrawalService.withdraw(user.getId());

        // Then
        assertThat(userRepository.findActiveById(user.getId())).isEmpty();
    }
}
