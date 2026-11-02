package com.chalkak.backend.like.infrastructure.persistence;

import com.chalkak.backend.like.domain.PostLike;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeJpaRepository extends JpaRepository<PostLike, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO post_likes (post_id, user_id)
            VALUES (:postId, :userId)
            ON CONFLICT (post_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int createIfAbsent(
            @Param("postId") UUID postId,
            @Param("userId") UUID userId
    );

    @Modifying
    @Query("""
            DELETE FROM PostLike postLike
            WHERE postLike.postId = :postId
              AND postLike.userId = :userId
            """)
    int deleteByPostIdAndUserId(
            @Param("postId") UUID postId,
            @Param("userId") UUID userId
    );

    @Modifying
    @Query("""
            DELETE FROM PostLike postLike
            WHERE postLike.postId = :postId
            """)
    int deleteByPostId(@Param("postId") UUID postId);

    @Query("""
            SELECT postLike.postId
            FROM PostLike postLike
            WHERE postLike.postId IN :postIds
              AND postLike.userId = :userId
            """)
    Set<UUID> findLikedPostIds(
            @Param("postIds") List<UUID> postIds,
            @Param("userId") UUID userId
    );

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);
}
