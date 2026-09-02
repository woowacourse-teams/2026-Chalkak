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
     * 회원이 있는지만 보고 정지는 통과시킨다. 정지 회원도 탈퇴와 자기 데이터 정리는 할 수 있어야
     * 하므로, 조회와 정리 경로에는 부재만 막고 정지는 그대로 통과시킨다.
     *
     * <p>{@link #validateUsable}로 대신할 수 없다. 그쪽은 정지를 403으로 막으므로 붙이는 순간
     * 정지된 회원이 서비스를 떠날 방법이 사라진다.
     */
    public boolean validateExisting(Authentication authentication) {
        getUser(authentication).validateNotWithdrawn();
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
     */
    private Optional<UUID> findUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(jwt.getSubject()));
    }
}
