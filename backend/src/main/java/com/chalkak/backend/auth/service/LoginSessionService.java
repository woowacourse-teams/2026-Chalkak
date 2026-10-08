package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class LoginSessionService {

    private final UserRepository userRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;

    public void lockSessions(Collection<UUID> sessionIds) {
        // 전체 폐기와 같은 UUID 순서로 잠그고 호출자의 트랜잭션이 끝날 때까지 유지한다.
        TreeSet<UUID> sortedSessionIds = new TreeSet<>(Comparator.comparing(UUID::toString));
        sortedSessionIds.addAll(sessionIds);
        sortedSessionIds.forEach(userRefreshTokenRepository::lockSession);
    }

    public User getUsableUser(
            UUID userId,
            UUID sessionId,
            Instant now
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(LoginSessionService::reauthenticationRequired);
        user.validateNotWithdrawn();
        if (!userRefreshTokenRepository.existsUsableBySessionIdAndUserId(sessionId, userId, now)) {
            throw reauthenticationRequired();
        }
        return user;
    }

    public Optional<User> findUsableUser(
            UUID userId,
            UUID sessionId,
            Instant now
    ) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.isDeleted()) {
            return Optional.empty();
        }
        if (!userRefreshTokenRepository.existsUsableBySessionIdAndUserId(sessionId, userId, now)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    private static UnauthorizedException reauthenticationRequired() {
        return new UnauthorizedException(ErrorCode.REAUTHENTICATION_REQUIRED, "다시 로그인해 주세요.");
    }
}
