package com.chalkak.backend.post.infrastructure.persistence;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostJpaRepository extends JpaRepository<Post, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT post
            FROM Post post
            WHERE post.id = :postId
              AND post.deletedAt IS NULL
            """)
    Optional<Post> findActiveByIdForUpdate(@Param("postId") UUID postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT post
            FROM Post post
            JOIN FETCH post.photo photo
            WHERE post.id = :postId
            """)
    Optional<Post> findByIdForUpdate(@Param("postId") UUID postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT post
            FROM Post post
            WHERE post.author.id = :authorId
              AND post.topic.id = :topicId
              AND post.moderationStatus <> :excludedStatus
              AND post.deletedAt IS NULL
            """)
    Optional<Post> findByAuthorIdAndTopicIdForUpdate(
            @Param("authorId") UUID authorId,
            @Param("topicId") UUID topicId,
            @Param("excludedStatus") ModerationStatus excludedStatus
    );

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

    @Lock(LockModeType.PESSIMISTIC_READ)
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
    Optional<Post> findVisibleByIdForShare(
            @Param("postId") UUID postId,
            @Param("moderationStatus") ModerationStatus moderationStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT post
            FROM Post post
            JOIN FETCH post.photo photo
            WHERE post.postImageUploadId = :postImageUploadId
              AND post.moderationStatus = :moderationStatus
              AND post.deletedAt IS NULL
              AND photo.deletedAt IS NULL
            """)
    Optional<Post> findByPostImageUploadIdForUpdate(
            @Param("postImageUploadId") UUID postImageUploadId,
            @Param("moderationStatus") ModerationStatus moderationStatus
    );

    @Query("""
            SELECT post
            FROM Post post
            JOIN FETCH post.topic topic
            JOIN FETCH post.photo photo
            WHERE post.author.id = :authorId
              AND topic.topicDate BETWEEN :startDate AND :endDate
              AND post.moderationStatus IN :moderationStatuses
              AND post.deletedAt IS NULL
              AND topic.deletedAt IS NULL
              AND photo.deletedAt IS NULL
            ORDER BY topic.topicDate ASC, post.id ASC
            """)
    List<Post> findCalendarPostsByAuthorIdAndTopicDateBetween(
            @Param("authorId") UUID authorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("moderationStatuses") Set<ModerationStatus> moderationStatuses
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

    @Query("""
            SELECT post
            FROM Post post
            JOIN post.topic topic
            JOIN FETCH post.photo photo
            JOIN FETCH post.author author
            LEFT JOIN PostLike postLike ON postLike.postId = post.id
            WHERE topic.id = :topicId
              AND post.moderationStatus = :moderationStatus
              AND post.deletedAt IS NULL
              AND topic.deletedAt IS NULL
              AND photo.deletedAt IS NULL
              AND author.deletedAt IS NULL
            GROUP BY post, photo, author
            ORDER BY COUNT(postLike) DESC, post.createdAt DESC, post.id ASC
            """)
    Slice<Post> findVisiblePopularByTopicId(
            @Param("topicId") UUID topicId,
            @Param("moderationStatus") ModerationStatus moderationStatus,
            Pageable pageable
    );
}
