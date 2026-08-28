package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.user.domain.User;
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
 *
 * <p>거부할 때 {@code false}를 돌려주면 공통 처리기가 사유를 알 수 없어 일반 문구만 내보낸다.
 * 정지는 사용자가 이유를 알아야 하는 상태라 예외로 던져 메시지를 그대로 전달한다.
 */
@Component
@RequiredArgsConstructor
public class UsableUserPolicy {

    private final UserRepository userRepository;

    public boolean isUsable(Authentication authentication) {
        Optional<User> user = findUser(authentication);
        if (user.isEmpty() || user.get().isActive()) {
            return true;
        }
        throw new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "이용이 정지된 회원입니다.");
    }

    /**
     * 없는 회원은 여기서 판단하지 않는다. 각 서비스가 자기 맥락에 맞는 메시지로 이미 처리한다.
     */
    private Optional<User> findUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return userRepository.findActiveById(UUID.fromString(jwt.getSubject()));
    }
}
