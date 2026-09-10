package com.chalkak.backend.post.repository;

import com.chalkak.backend.post.domain.Post;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository {

    Post save(Post post);

    Optional<Post> findActiveByAuthorIdAndTopicIdForUpdate(UUID authorId, UUID topicId);

    /**
     * 위 잠금 조회와 조건은 같고 잠금만 걸지 않는다. 작성 가능 여부만 미리 알려 주는 읽기 전용 조회는
     * 게시물 행을 잠글 이유가 없다.
     */
    Optional<Post> findActiveByAuthorIdAndTopicId(UUID authorId, UUID topicId);

    Optional<Post> findActiveByIdForUpdate(UUID postId);

    Optional<Post> findByIdForUpdate(UUID postId);

    /**
     * 지금까지의 변경을 즉시 반영한다. 활성 게시물을 가리는 부분 유니크 인덱스가 있어, 새 게시물을 저장하기
     * 전에 기존 게시물의 거절 처리가 먼저 DB에 닿아야 한다.
     */
    void flush();

    Optional<Post> findVisibleById(UUID postId);

    Optional<Post> findVisibleByIdForShare(UUID postId);

    Optional<Post> findValidatingByPostImageUploadIdForUpdate(UUID postImageUploadId);

    List<Post> findCalendarPostsByAuthorIdAndTopicDateBetween(
            UUID authorId,
            LocalDate startDate,
            LocalDate endDate
    );

    PostSlice findVisibleRecentByTopicId(
            UUID topicId,
            int page,
            int pageSize
    );

    PostSlice findVisibleRandomByTopicId(
            UUID topicId,
            String randomSeed,
            int page,
            int pageSize
    );

    PostSlice findVisiblePopularByTopicId(
            UUID topicId,
            int page,
            int pageSize
    );
}
