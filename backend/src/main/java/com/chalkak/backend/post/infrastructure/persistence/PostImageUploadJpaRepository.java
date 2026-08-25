package com.chalkak.backend.post.infrastructure.persistence;

import com.chalkak.backend.post.domain.PostImageUpload;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostImageUploadJpaRepository extends JpaRepository<PostImageUpload, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT upload
            FROM PostImageUpload upload
            WHERE upload.id = :uploadId
            """)
    Optional<PostImageUpload> findByIdForUpdate(@Param("uploadId") UUID uploadId);
}
