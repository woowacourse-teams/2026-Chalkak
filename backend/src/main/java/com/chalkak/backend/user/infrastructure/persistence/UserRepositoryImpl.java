package com.chalkak.backend.user.infrastructure.persistence;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserStatus;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public List<UUID> findTopicPushRecipientIds(UUID topicId) {
        return userJpaRepository.findTopicPushRecipientIds(UserStatus.ACTIVE, topicId,
                ModerationStatus.REJECTED);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJpaRepository.findById(id);
    }

    @Override
    public Optional<User> findActiveById(UUID id) {
        return userJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<User> findActiveByIdForUpdate(UUID id) {
        return userJpaRepository.findActiveByIdForUpdate(id);
    }

    @Override
    public Optional<User> findByIdForUpdate(UUID id) {
        return userJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public Optional<User> findActiveByPendingSignatureUploadIdForUpdate(UUID uploadId) {
        return userJpaRepository.findActiveByPendingSignatureUploadIdForUpdate(uploadId);
    }

    @Override
    public boolean existsBySignatureOriginalStorageKey(String storageKey) {
        return userJpaRepository.existsBySignatureOriginalStorageKey(storageKey);
    }

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }
}
