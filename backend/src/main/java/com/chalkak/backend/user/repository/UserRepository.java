package com.chalkak.backend.user.repository;

import com.chalkak.backend.user.domain.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findActiveById(UUID id);

    boolean existsBySignatureOriginalStorageKey(String storageKey);

    User save(User user);
}
