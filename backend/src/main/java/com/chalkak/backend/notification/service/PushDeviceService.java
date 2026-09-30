package com.chalkak.backend.notification.service;

import com.chalkak.backend.auth.domain.LoginSession;
import com.chalkak.backend.auth.repository.LoginSessionRepository;
import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.notification.domain.FcmToken;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushDeviceService {

    private final PushDeviceRepository pushDeviceRepository;
    private final LoginSessionRepository loginSessionRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final Clock clock;

    @Transactional
    public void register(
            UUID userId,
            UUID sessionId,
            String fcmToken
    ) {
        if (userId == null || sessionId == null) {
            throw reauthenticationRequired();
        }
        FcmToken token = new FcmToken(fcmToken);
        pushDeviceRepository.lockToken(token.getHash());
        lockSessions(sessionId, token.getHash());

        Instant now = clock.instant();
        LoginSession session = loginSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(PushDeviceService::reauthenticationRequired);
        session.getUser().validateNotWithdrawn();
        if (!userRefreshTokenRepository.existsUsableBySessionIdAndUserId(sessionId, userId, now)) {
            throw reauthenticationRequired();
        }

        pushDeviceRepository.disableOtherSessionByTokenHash(token.getHash(), sessionId, now);
        PushDevice device = pushDeviceRepository.findBySessionId(sessionId)
                .orElseGet(() -> new PushDevice(session, token, now));
        device.updateToken(token, now);
        pushDeviceRepository.save(device);
    }

    private void lockSessions(UUID sessionId, String tokenHash) {
        // PostgreSQL UUID 정렬과 같은 순서로 잠가 토큰 교환 등록·전체 로그아웃의 교착을 막는다.
        TreeSet<UUID> sessionIds = new TreeSet<>(Comparator.comparing(UUID::toString));
        sessionIds.add(sessionId);
        pushDeviceRepository.findActiveSessionIdByTokenHash(tokenHash).ifPresent(sessionIds::add);
        sessionIds.forEach(userRefreshTokenRepository::lockSession);
    }

    private static UnauthorizedException reauthenticationRequired() {
        return new UnauthorizedException(ErrorCode.REAUTHENTICATION_REQUIRED, "다시 로그인해 주세요.");
    }
}
