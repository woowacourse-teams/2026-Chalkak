package com.chalkak.backend.user.infrastructure.persistence;

import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<User, UUID> {

    @Query("""
            SELECT user.id FROM User user
            WHERE user.deletedAt IS NULL
                AND user.status = :status
                AND user.topicPushEnabled = true
            ORDER BY user.id
            """)
    List<UUID> findTopicPushRecipientIds(@Param("status") UserStatus status);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findActiveByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from User u
            where u.pendingSignatureUploadId = :uploadId
              and u.deletedAt is null
            """)
    Optional<User> findActiveByPendingSignatureUploadIdForUpdate(@Param("uploadId") UUID uploadId);

    boolean existsBySignatureOriginalStorageKey(String storageKey);
}
