package com.chalkak.backend.user.repository;

import com.chalkak.backend.user.domain.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    /**
     * 탈퇴하지 않은 사용자만 조회한다. 삭제 필터는 구현체에만 두고 호출부로 새어 나가지 않게 한다.
     */
    Optional<User> findActiveById(UUID id);

    User save(User user);
}
