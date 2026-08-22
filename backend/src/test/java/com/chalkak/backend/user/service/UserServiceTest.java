package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.IntegrationTestSupport;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserServiceTest extends IntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("탈퇴하면 개인 식별 정보가 비식별화되고 탈퇴 시각이 기록된다")
    void withdraw_activeUser_anonymizesPersonalData() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        // When
        userService.withdraw(id);
        flushAndClear();

        // Then
        User withdrawn = userRepository.findById(id).orElseThrow();
        assertThat(withdrawn.getEmail()).isEqualTo("withdrawn+" + id + "@chalkak.invalid");
        assertThat(withdrawn.getSignatureOriginalStorageKey()).isEqualTo("withdrawn/" + id);
        assertThat(withdrawn.getSignatureThumbnailStorageKey()).isNull();
        assertThat(withdrawn.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("탈퇴한 회원은 살아있는 회원 조회에서 제외된다")
    void withdraw_activeUser_excludesFromActiveQuery() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        // When
        userService.withdraw(id);
        flushAndClear();

        // Then
        assertThat(userRepository.findActiveById(id)).isEmpty();
        assertThat(userRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("존재하지 않는 회원의 탈퇴는 거부한다")
    void withdraw_notExistingUser_throwsNotFoundException() {
        // Given
        UUID notExistingId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> userService.withdraw(notExistingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("탈퇴할 회원을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("이미 탈퇴한 회원은 존재하지 않는 회원과 동일하게 거부한다")
    void withdraw_alreadyWithdrawnUser_throwsNotFoundException() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        userService.withdraw(id);
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userService.withdraw(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("탈퇴할 회원을 찾을 수 없습니다.");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
