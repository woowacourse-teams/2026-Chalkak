package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * {@link RequiresUsableUser}가 참조하는 회원 이용 가능 판정. 상태는 토큰이 아니라 저장소에서
 * 읽는다. 토큰에 담으면 정지시켜도 만료될 때까지 계속 통하기 때문이다.
 *
 * <p>없는 회원도 여기서 판단한다. 각 서비스에 맡기면 같은 실패가 화면마다 다른 상태 코드와
 * 문구로 흩어지므로, 인증 정보가 가리키는 회원이 없다는 사실은 한곳에서 401로 답한다.
 */
@Component
@RequiredArgsConstructor
public class UsableUserPolicy {

    private final UserRepository userRepository;

    /**
     * 통과하면 {@code true}를 돌려주고, 막을 때는 예외를 던진다. 거부 사유를 {@code false}로 접으면
     * 공통 처리기가 이유를 알 수 없어 일반 문구만 나가는데, 정지는 사용자가 이유를 알아야 한다.
     */
    public boolean validateUsable(Authentication authentication) {
        getUser(authentication).validateAccessible();
        return true;
    }

    /**
     * 탈퇴 회원까지 읽어야 이미 없는 회원과 구분할 수 있으므로 활성 회원만 거르지 않는다.
     */
    private User getUser(Authentication authentication) {
        UUID userId = findUserId(authentication)
                .orElseThrow(() -> new ForbiddenException(
                        ErrorCode.FORBIDDEN,
                        "접근 권한이 없습니다."));

        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.UNAUTHORIZED,
                        "유효하지 않은 인증 정보입니다."));
    }

    /**
     * 이 판정은 인증이 끝난 엔드포인트에만 붙인다. 주체를 읽을 수 없다는 것은 표시가 잘못 붙었다는
     * 뜻이므로 통과시키지 않고 막는다.
     *
     * <p>관리자 토큰의 {@code sub}는 회원 식별자가 아니라 회원 저장소에 없다. 그대로 조회하면
     * 권한 부족이 없는 회원으로 접혀 403이 401로 바뀌므로, 식별자를 돌려주기 전에 걸러낸다.
     */
    private Optional<UUID> findUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        AuthenticatedUsers.validateNotAdmin(authentication);
        return Optional.of(UUID.fromString(jwt.getSubject()));
    }
}
