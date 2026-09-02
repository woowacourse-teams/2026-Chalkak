package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * {@link RequiresUsableUser}가 참조하는 정지 회원 판정. 상태는 토큰이 아니라 저장소에서 읽는다.
 * 토큰에 담으면 정지시켜도 만료될 때까지 계속 통하기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class UsableUserPolicy {

    private final UserRepository userRepository;

    /**
     * 통과하면 {@code true}를 돌려주고, 막을 때는 예외를 던진다. 거부 사유를 {@code false}로 접으면
     * 공통 처리기가 이유를 알 수 없어 일반 문구만 나가는데, 정지는 사용자가 이유를 알아야 한다.
     *
     * <p>없는 회원은 여기서 판단하지 않는다. 각 서비스가 자기 맥락에 맞는 메시지로 이미 처리한다.
     */
    public boolean validateUsable(Authentication authentication) {
        UUID userId = findUserId(authentication)
                .orElseThrow(() -> new ForbiddenException(
                        ErrorCode.FORBIDDEN,
                        "접근 권한이 없습니다."));

        userRepository.findActiveById(userId)
                .ifPresent(user -> user.validateAccessible());
        return true;
    }

    /**
     * 이 판정은 인증이 끝난 엔드포인트에만 붙인다. 주체를 읽을 수 없다는 것은 표시가 잘못 붙었다는
     * 뜻이므로, 회원이 없는 경우와 달리 통과시키지 않고 막는다.
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
