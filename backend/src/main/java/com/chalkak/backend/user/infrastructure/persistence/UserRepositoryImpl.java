package com.chalkak.backend.user.infrastructure.persistence;

import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findById(id);
    }

    @Override
    public Optional<User> findActiveById(UUID id) {
        return userJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }
}
