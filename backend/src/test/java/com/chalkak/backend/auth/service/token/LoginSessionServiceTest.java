package com.chalkak.backend.auth.service.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.domain.UserStatus;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657a02");
    private static final UUID SESSION_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657a03");

    @InjectMocks
    private LoginSessionService loginSessionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRefreshTokenRepository userRefreshTokenRepository;

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("유효한 로그인은 정상 회원과 정지 회원을 모두 반환한다")
    void findUsableUser_liveSession_returnsUser(UserStatus status) {
        // Given
        User user = status == UserStatus.BANNED
                ? UserFixture.createBanned(USER_ID)
                : UserFixture.create(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userRefreshTokenRepository.existsUsableBySessionIdAndUserId(
                SESSION_ID,
                USER_ID,
                NOW
        )).willReturn(true);

        // When
        Optional<User> result = loginSessionService.findUsableUser(
                USER_ID,
                SESSION_ID,
                NOW
        );

        // Then
        assertThat(result).containsSame(user);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("회원이 없거나 탈퇴했으면 로그인 상태를 조회하지 않고 빈 결과를 반환한다")
    void findUsableUser_unavailableUser_returnsEmpty(boolean withdrawn) {
        // Given
        Optional<User> user = Optional.empty();
        if (withdrawn) {
            User withdrawnUser = UserFixture.create(USER_ID);
            withdrawnUser.withdraw();
            user = Optional.of(withdrawnUser);
        }
        given(userRepository.findById(USER_ID)).willReturn(user);

        // When
        Optional<User> result = loginSessionService.findUsableUser(
                USER_ID,
                SESSION_ID,
                NOW
        );

        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(userRefreshTokenRepository);
    }

    @Test
    @DisplayName("사용할 수 없는 로그인은 예외 없이 빈 결과를 반환한다")
    void findUsableUser_unusableSession_returnsEmpty() {
        // Given
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(userRefreshTokenRepository.existsUsableBySessionIdAndUserId(
                SESSION_ID,
                USER_ID,
                NOW
        )).willReturn(false);

        // When
        Optional<User> result = loginSessionService.findUsableUser(
                USER_ID,
                SESSION_ID,
                NOW
        );

        // Then
        assertThat(result).isEmpty();
    }
}
