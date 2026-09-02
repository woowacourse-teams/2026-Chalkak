package com.chalkak.backend.admin.repository;

import com.chalkak.backend.admin.domain.Admin;
import java.util.Optional;
import java.util.UUID;

public interface AdminRepository {

    Optional<Admin> findByUsername(String username);

    Optional<Admin> findById(UUID adminId);

    Admin save(Admin admin);
}
