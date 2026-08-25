package com.chalkak.backend.post.infrastructure.persistence;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostJpaRepository extends JpaRepository<Post, UUID> {

    boolean existsByAuthorIdAndTopicIdAndDeletedAtIsNull(UUID authorId, UUID topicId);

    @Query("""
            SELECT post
            FROM Post post
            JOIN FETCH post.topic topic
            JOIN FETCH post.photo photo
            JOIN FETCH post.author author
            WHERE post.id = :postId
              AND post.moderationStatus = :moderationStatus
              AND post.deletedAt IS NULL
              AND topic.deletedAt IS NULL
              AND photo.deletedAt IS NULL
              AND author.deletedAt IS NULL
            """)
    Optional<Post> findVisibleById(
            @Param("postId") UUID postId,
            @Param("moderationStatus") ModerationStatus moderationStatus
    );

    @Query("""
            SELECT post
            FROM Post post
            JOIN post.topic topic
            JOIN FETCH post.photo photo
            JOIN FETCH post.author author
            WHERE topic.id = :topicId
              AND post.moderationStatus = :moderationStatus
              AND post.deletedAt IS NULL
              AND topic.deletedAt IS NULL
              AND photo.deletedAt IS NULL
              AND author.deletedAt IS NULL
            """)
    Slice<Post> findVisibleByTopicId(
            @Param("topicId") UUID topicId,
            @Param("moderationStatus") ModerationStatus moderationStatus,
            Pageable pageable
    );

    @Query("""
            SELECT post
            FROM Post post
            JOIN post.topic topic
            JOIN FETCH post.photo photo
            JOIN FETCH post.author author
            WHERE topic.id = :topicId
              AND post.moderationStatus = :moderationStatus
              AND post.deletedAt IS NULL
              AND topic.deletedAt IS NULL
              AND photo.deletedAt IS NULL
              AND author.deletedAt IS NULL
            ORDER BY function(
                'md5',
                concat(cast(post.id as String), :randomSeed)
            ), post.id ASC
            """)
    Slice<Post> findVisibleRandomByTopicId(
            @Param("topicId") UUID topicId,
            @Param("moderationStatus") ModerationStatus moderationStatus,
            @Param("randomSeed") String randomSeed,
            Pageable pageable
    );
}
