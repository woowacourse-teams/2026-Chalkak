package com.chalkak.backend.filter.infrastructure.persistence;

import com.chalkak.backend.filter.domain.Filter;
import com.chalkak.backend.post.domain.PostImageUpload;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FilterJpaRepository extends JpaRepository<Filter, UUID> {

    @Query("""
            SELECT f FROM Filter f
            JOIN f.artist a
            WHERE a.id = :artistId AND a.deletedAt IS NULL AND f.deletedAt IS NULL
                AND (a.id = :viewerId OR f.status = com.chalkak.backend.filter.domain.FilterStatus.PUBLIC)
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    Slice<Filter> findVisibleByArtistId(
            @Param("artistId") UUID artistId,
            @Param("viewerId") UUID viewerId,
            Pageable pageable
    );

    @Query("""
            SELECT f FROM Filter f
            JOIN f.artist a
            WHERE f.id = :filterId AND a.deletedAt IS NULL AND f.deletedAt IS NULL
                AND (a.id = :viewerId OR f.status = com.chalkak.backend.filter.domain.FilterStatus.PUBLIC)
            """)
    Optional<Filter> findVisibleById(@Param("filterId") UUID filterId,
            @Param("viewerId") UUID viewerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Filter f WHERE f.id = :filterId AND f.deletedAt IS NULL")
    Optional<Filter> findActiveByIdForUpdate(@Param("filterId") UUID filterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Filter f WHERE f.id = :filterId")
    Optional<Filter> findByIdForUpdate(@Param("filterId") UUID filterId);

    @Query(value = """
            SELECT u.* FROM filter_photos p
            JOIN post_image_uploads u ON u.id = p.upload_id
            WHERE p.filter_id = :filterId
            ORDER BY p.sort_order
            """, nativeQuery = true)
    List<PostImageUpload> findPhotoUploads(@Param("filterId") UUID filterId);

    @Modifying
    @Query(value = "DELETE FROM filter_photos WHERE filter_id = :filterId", nativeQuery = true)
    void deletePhotoUploads(@Param("filterId") UUID filterId);

    @Modifying
    @Query(value = """
            INSERT INTO filter_photos (filter_id, upload_id, sort_order)
            VALUES (:filterId, :uploadId, :sortOrder)
            """, nativeQuery = true)
    void insertPhotoUpload(
            @Param("filterId") UUID filterId,
            @Param("uploadId") UUID uploadId,
            @Param("sortOrder") int sortOrder
    );
}
