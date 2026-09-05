package com.chalkak.backend.auth.repository;

import com.chalkak.backend.auth.domain.ConsumedSignupToken;
import java.time.Instant;

public interface ConsumedSignupTokenRepository {

    /**
     * 아직 소진되지 않은 토큰이면 저장하고 {@code true}를 반환한다. 같은 jti가 이미
     * 저장돼 있으면 저장하지 않고 {@code false}를 반환한다.
     */
    boolean consumeIfAbsent(ConsumedSignupToken token);

    void deleteAllExpiredBefore(Instant now);
}
