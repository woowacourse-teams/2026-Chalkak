package com.chalkak.backend.auth.repository;

import com.chalkak.backend.user.domain.User;
import java.util.UUID;

public interface LoginSessionRepository {

    void create(User user, UUID sessionId);
}
