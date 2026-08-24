package com.chalkak.backend.user.infrastructure.persistence;

import com.chalkak.backend.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsBySignatureOriginalStorageKey(String storageKey);
}
