package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.domain.Admin;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminJpaRepository extends JpaRepository<Admin, UUID> {

    Optional<Admin> findByUsername(String username);
}
