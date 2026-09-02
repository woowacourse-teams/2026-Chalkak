package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminRepositoryImpl implements AdminRepository {

    private final AdminJpaRepository adminJpaRepository;

    @Override
    public Optional<Admin> findByUsername(String username) {
        return adminJpaRepository.findByUsername(username);
    }

    @Override
    public Optional<Admin> findById(UUID adminId) {
        return adminJpaRepository.findById(adminId);
    }

    @Override
    public Admin save(Admin admin) {
        return adminJpaRepository.save(admin);
    }
}
