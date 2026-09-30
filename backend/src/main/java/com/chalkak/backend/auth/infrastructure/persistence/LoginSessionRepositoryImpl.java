package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.LoginSession;
import com.chalkak.backend.auth.repository.LoginSessionRepository;
import com.chalkak.backend.user.domain.User;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LoginSessionRepositoryImpl implements LoginSessionRepository {

    private final LoginSessionJpaRepository loginSessionJpaRepository;

    @Override
    public void create(User user, UUID sessionId) {
        // 토큰은 회차를 엔티티 관계가 아닌 UUID로 참조하므로 회차 INSERT를 먼저 실행한다.
        loginSessionJpaRepository.saveAndFlush(new LoginSession(user, sessionId));
    }
}
