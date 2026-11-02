package com.chalkak.backend.like.infrastructure.persistence;

import com.chalkak.backend.like.domain.PostLikeCounter;
import com.chalkak.backend.like.repository.PostLikeCount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeCounterJpaRepository extends JpaRepository<PostLikeCounter, UUID> {

    @Query("SELECT counter.likeCount FROM PostLikeCounter counter WHERE counter.postId = :postId")
    Optional<Long> findCountByPostId(@Param("postId") UUID postId);

    @Query("""
            SELECT new com.chalkak.backend.like.repository.PostLikeCount(
                counter.postId, counter.likeCount
            )
            FROM PostLikeCounter counter
            WHERE counter.postId IN :postIds
            """)
    List<PostLikeCount> findCountsByPostIds(@Param("postIds") List<UUID> postIds);

    // 게시물 INSERT가 먼저 DB에 반영돼야 집계 행의 외래 키를 만족한다.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO post_like_counts (post_id, like_count)
            VALUES (:postId, 0)
            """, nativeQuery = true)
    int create(@Param("postId") UUID postId);

    @Modifying
    @Query(value = """
            UPDATE post_like_counts SET like_count = like_count + 1
            WHERE post_id = :postId
            """, nativeQuery = true)
    int increment(@Param("postId") UUID postId);

    @Modifying
    @Query(value = """
            UPDATE post_like_counts SET like_count = like_count - 1
            WHERE post_id = :postId
            """, nativeQuery = true)
    int decrement(@Param("postId") UUID postId);

    @Modifying
    @Query(value = """
            UPDATE post_like_counts SET like_count = 0
            WHERE post_id = :postId
            """, nativeQuery = true)
    int reset(@Param("postId") UUID postId);
}
