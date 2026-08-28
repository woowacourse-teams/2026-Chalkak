package com.chalkak.backend.auth.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 정지 판정은 저장소를 읽어야 하므로 포트를 Mock으로 대체한다. 이 클래스는 Service가 아니라
 * 인가 판정 지점이고, 검증 대상은 저장소 조회 결과가 아니라 그 결과를 어떻게 해석하느냐다.
 */
class UsableUserPolicyTest {

    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final UsableUserPolicy policy = new UsableUserPolicy(userRepository);

    @Test
    @DisplayName("정지된 회원은 거부한다")
    void validateUsable_suspendedUser_throwsForbiddenException() {
        // Given
        UUID userId = UUID.randomUUID();
        given(userRepository.findActiveById(userId))
                .willReturn(Optional.of(UserFixture.createBanned(userId)));

        // When & Then
        assertThatThrownBy(() -> policy.validateUsable(authenticationOf(userId)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("이용이 정지된 회원입니다.");
    }

    @Test
    @DisplayName("정상 회원은 통과시킨다")
    void validateUsable_activeUser_returnsTrue() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = UserFixture.create(userId);
        given(userRepository.findActiveById(userId)).willReturn(Optional.of(user));

        // When & Then
        assertThat(policy.validateUsable(authenticationOf(userId))).isTrue();
    }

    /**
     * 없는 회원은 여기서 판단하지 않는다. 각 서비스가 자기 맥락에 맞는 메시지로 이미 처리한다.
     */
    @Test
    @DisplayName("저장소에 없는 회원은 서비스가 판단하도록 통과시킨다")
    void validateUsable_unknownUser_returnsTrue() {
        // Given
        UUID userId = UUID.randomUUID();
        given(userRepository.findActiveById(userId)).willReturn(Optional.empty());

        // When & Then
        assertThat(policy.validateUsable(authenticationOf(userId))).isTrue();
    }

    /**
     * 이 판정은 인증이 끝난 엔드포인트에만 붙인다. 주체를 읽을 수 없다는 것은 표시가 잘못
     * 붙었다는 뜻이므로, 열리는 쪽이 아니라 막히는 쪽으로 기울어야 한다.
     */
    @Test
    @DisplayName("인증 주체가 없으면 거부한다")
    void validateUsable_noAuthentication_throwsForbiddenException() {
        // When & Then
        assertThatThrownBy(() -> policy.validateUsable(null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("액세스 토큰으로 인증되지 않은 주체는 거부한다")
    void validateUsable_nonJwtPrincipal_throwsForbiddenException() {
        // Given
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        // When & Then
        assertThatThrownBy(() -> policy.validateUsable(anonymous))
                .isInstanceOf(ForbiddenException.class);
    }

    private Authentication authenticationOf(UUID userId) {
        Instant issuedAt = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("purpose", "ACCESS")
                .build();

        return new JwtAuthenticationToken(jwt, List.of());
    }
}
