package com.chalkak.backend.auth.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
        given(userRepository.findById(userId))
                .willReturn(Optional.of(UserFixture.createBanned(userId)));

        // When & Then
        assertThatThrownBy(() -> policy.validateUsable(authenticationOf(userId)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("차단된 회원입니다.")
                .satisfies(exception -> assertThat(((ForbiddenException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("정상 회원은 통과시킨다")
    void validateUsable_activeUser_returnsTrue() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = UserFixture.create(userId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // When & Then
        assertThat(policy.validateUsable(authenticationOf(userId))).isTrue();
    }

    /**
     * 없는 회원은 인증 정보가 가리키는 대상이 사라졌다는 뜻이므로 인가가 아니라 인증의 실패다.
     * 각 서비스가 자기 맥락의 404로 답하면 같은 원인이 화면마다 다른 문구로 흩어진다.
     */
    @Test
    @DisplayName("저장소에 없는 회원은 거부한다")
    void validateUsable_unknownUser_throwsUnauthorizedException() {
        // Given
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> policy.validateUsable(authenticationOf(userId)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.")
                .satisfies(exception ->
                        assertThat(((UnauthorizedException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 탈퇴 회원은 저장소에 남아 있지만 인증 주체로는 없는 회원과 같다. 남아 있다는 사실이
     * 응답에 드러나지 않도록 없는 회원과 같은 답을 준다.
     */
    @Test
    @DisplayName("탈퇴한 회원은 거부한다")
    void validateUsable_withdrawnUser_throwsUnauthorizedException() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = UserFixture.create(userId);
        user.withdraw();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(() -> policy.validateUsable(authenticationOf(userId)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.")
                .satisfies(exception ->
                        assertThat(((UnauthorizedException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 관리자 토큰의 {@code sub}는 회원 식별자가 아니라서 저장소에는 없다. 없는 회원으로 접어
     * 401로 답하면 권한 문제가 인증 문제로 바뀌므로, 저장소를 읽기 전에 걸러야 한다.
     */
    @Test
    @DisplayName("관리자 토큰은 일반 사용자 권한 부족으로 거부한다")
    void validateUsable_adminToken_throwsForbiddenException() {
        // Given
        Authentication authentication = authenticationOf(
                UUID.randomUUID(),
                List.of(new SimpleGrantedAuthority(AccessTokenScope.ADMIN.toAuthority())));

        // When & Then
        assertThatThrownBy(() -> policy.validateUsable(authentication))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("일반 사용자 권한이 필요합니다.")
                .satisfies(exception -> assertThat(((ForbiddenException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
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

    @Test
    @DisplayName("정상 회원은 통과시킨다")
    void validateExisting_activeUser_returnsTrue() {
        // given
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId))
                .willReturn(Optional.of(UserFixture.create(userId)));

        // when
        boolean result = policy.validateExisting(authenticationOf(userId));

        // then
        assertThat(result).isTrue();
    }

    /**
     * 정지 회원도 탈퇴와 자기 데이터 정리는 할 수 있어야 하므로, 이 판정은 정지를 막지 않는다.
     * 여기서 막으면 정지된 회원이 서비스를 떠날 방법 자체가 사라진다.
     */
    @Test
    @DisplayName("정지된 회원도 통과시킨다")
    void validateExisting_suspendedUser_returnsTrue() {
        // given
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId))
                .willReturn(Optional.of(UserFixture.createBanned(userId)));

        // when
        boolean result = policy.validateExisting(authenticationOf(userId));

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("탈퇴한 회원은 거부한다")
    void validateExisting_withdrawnUser_throwsUnauthorizedException() {
        // given
        UUID userId = UUID.randomUUID();
        User user = UserFixture.create(userId);
        user.withdraw();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> policy.validateExisting(authenticationOf(userId)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.")
                .satisfies(exception ->
                        assertThat(((UnauthorizedException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("저장소에 없는 회원은 거부한다")
    void validateExisting_unknownUser_throwsUnauthorizedException() {
        // given
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> policy.validateExisting(authenticationOf(userId)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("유효하지 않은 인증 정보입니다.")
                .satisfies(exception ->
                        assertThat(((UnauthorizedException) exception).getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("관리자 토큰은 일반 사용자 권한 부족으로 거부한다")
    void validateExisting_adminToken_throwsForbiddenException() {
        // given
        Authentication authentication = authenticationOf(
                UUID.randomUUID(),
                List.of(new SimpleGrantedAuthority(AccessTokenScope.ADMIN.toAuthority())));

        // when & then
        assertThatThrownBy(() -> policy.validateExisting(authentication))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("일반 사용자 권한이 필요합니다.")
                .satisfies(exception -> assertThat(((ForbiddenException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    private Authentication authenticationOf(UUID userId) {
        return authenticationOf(userId, List.of());
    }

    private Authentication authenticationOf(
            UUID userId,
            Collection<? extends GrantedAuthority> authorities) {
        Instant issuedAt = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600))
                .claim("purpose", "ACCESS")
                .build();

        return new JwtAuthenticationToken(jwt, authorities);
    }
}
