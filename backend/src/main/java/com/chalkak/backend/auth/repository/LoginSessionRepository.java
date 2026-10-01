package com.chalkak.backend.auth.repository;

import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.auth.domain.LoginSession;
import java.util.Optional;
import java.util.UUID;

public interface LoginSessionRepository {

    void create(User user, UUID sessionId);

    Optional<LoginSession> findByIdAndUserId(UUID sessionId, UUID userId);
}
