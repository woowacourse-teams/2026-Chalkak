package com.chalkak.backend.post.infrastructure.persistence;

import com.chalkak.backend.post.domain.PostImageUpload;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
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

    /**
     * 상태만 스칼라로 읽는다. 엔티티로 읽으면 영속성 컨텍스트에 올라가, 뒤이은 잠금 조회가 락은 잡되
     * 이미 적재된 낡은 상태를 그대로 돌려준다.
     */
    @Query("""
            SELECT upload.status
            FROM PostImageUpload upload
            WHERE upload.id = :uploadId
              AND upload.user.id = :userId
            """)
    Optional<PostImageUploadStatus> findStatusByIdAndUserId(
            @Param("uploadId") UUID uploadId,
            @Param("userId") UUID userId
    );
}
