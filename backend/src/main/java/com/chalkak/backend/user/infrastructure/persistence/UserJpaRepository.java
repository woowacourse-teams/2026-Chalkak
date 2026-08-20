package com.chalkak.backend.user.infrastructure.persistence;

import com.chalkak.backend.user.domain.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, UUID> {
}
