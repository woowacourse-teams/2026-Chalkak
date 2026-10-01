package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.LoginSession;
import com.chalkak.backend.auth.repository.LoginSessionRepository;
import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
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

    private final LoginSessionRepository loginSessionRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;

    public void lockSessions(Collection<UUID> sessionIds) {
        // 전체 폐기와 같은 UUID 순서로 잠그고 호출자의 트랜잭션이 끝날 때까지 유지한다.
        TreeSet<UUID> sortedSessionIds = new TreeSet<>(Comparator.comparing(UUID::toString));
        sortedSessionIds.addAll(sessionIds);
        sortedSessionIds.forEach(userRefreshTokenRepository::lockSession);
    }

    public LoginSession getUsableSession(
            UUID userId,
            UUID sessionId,
            Instant now
    ) {
        LoginSession session = loginSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(LoginSessionService::reauthenticationRequired);
        session.getUser().validateNotWithdrawn();
        if (!userRefreshTokenRepository.existsUsableBySessionIdAndUserId(sessionId, userId, now)) {
            throw reauthenticationRequired();
        }
        return session;
    }

    private static UnauthorizedException reauthenticationRequired() {
        return new UnauthorizedException(ErrorCode.REAUTHENTICATION_REQUIRED, "다시 로그인해 주세요.");
    }
}
