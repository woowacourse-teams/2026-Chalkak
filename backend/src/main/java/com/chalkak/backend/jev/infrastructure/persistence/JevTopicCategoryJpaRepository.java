package com.chalkak.backend.jev.infrastructure.persistence;

import com.chalkak.backend.jev.domain.JevTopicCategory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JevTopicCategoryJpaRepository extends JpaRepository<JevTopicCategory, UUID> {

    @Query(value = "SELECT pg_advisory_xact_lock(1056, hashtext(:key))", nativeQuery = true)
    void lockClassificationKey(@Param("key") String key);

    Optional<JevTopicCategory> findByClassificationKey(String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT category FROM JevTopicCategory category WHERE category.id = :id")
    Optional<JevTopicCategory> findForUpdate(@Param("id") UUID id);
}
