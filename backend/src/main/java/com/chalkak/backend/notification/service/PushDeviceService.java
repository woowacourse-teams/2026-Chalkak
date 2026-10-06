package com.chalkak.backend.notification.service;

import com.chalkak.backend.auth.service.LoginSessionService;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.notification.domain.FcmToken;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushDeviceService {

    private final PushDeviceRepository pushDeviceRepository;
    private final LoginSessionService loginSessionService;
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
        User user = loginSessionService.getUsableUser(userId, sessionId, now);

        pushDeviceRepository.deleteOtherSessionByTokenHash(token.getHash(), sessionId);
        PushDevice device = pushDeviceRepository.findBySessionId(sessionId)
                .orElseGet(() -> new PushDevice(user, sessionId, token, now));
        device.updateToken(token, now);
        pushDeviceRepository.save(device);
    }

    @Transactional
    public void deleteBySessionId(UUID sessionId) {
        pushDeviceRepository.deleteBySessionId(sessionId);
    }

    @Transactional
    public void deleteByUserId(UUID userId) {
        pushDeviceRepository.deleteByUserId(userId);
    }

    private void lockSessions(UUID sessionId, String tokenHash) {
        List<UUID> sessionIds = new ArrayList<>();
        sessionIds.add(sessionId);
        pushDeviceRepository.findSessionIdByTokenHash(tokenHash).ifPresent(sessionIds::add);
        loginSessionService.lockSessions(sessionIds);
    }

    private static UnauthorizedException reauthenticationRequired() {
        return new UnauthorizedException(ErrorCode.REAUTHENTICATION_REQUIRED, "다시 로그인해 주세요.");
    }
}
