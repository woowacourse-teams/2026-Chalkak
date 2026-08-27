package com.chalkak.backend.admin.repository;

import com.chalkak.backend.admin.domain.Admin;
import java.util.Optional;

public interface AdminRepository {

    Optional<Admin> findByUsername(String username);

    Admin save(Admin admin);
}
