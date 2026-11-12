package com.chalkak.backend.filter.infrastructure.persistence;

import com.chalkak.backend.filter.domain.Filter;
import com.chalkak.backend.post.domain.PostImageUpload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query(value = """
            SELECT u.* FROM filter_photos p
            JOIN post_image_uploads u ON u.id = p.upload_id
            WHERE p.filter_id = :filterId
            ORDER BY p.sort_order
            """, nativeQuery = true)
    List<PostImageUpload> findPhotoUploads(@Param("filterId") UUID filterId);
}
