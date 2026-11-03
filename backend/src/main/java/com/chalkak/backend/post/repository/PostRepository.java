package com.chalkak.backend.post.repository;

import com.chalkak.backend.post.domain.Post;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository {

    Post save(Post post);

    Optional<Post> findActiveByAuthorIdAndTopicIdForUpdate(UUID authorId, UUID topicId);

    /**
     * 위 잠금 조회와 조건은 같고 잠금만 걸지 않는다. 작성 가능 여부만 미리 알려 주는 읽기 전용 조회는 게시물 행을 잠글 이유가 없다.
     */
    Optional<Post> findActiveByAuthorIdAndTopicId(UUID authorId, UUID topicId);

    Optional<Post> findActiveByIdForUpdate(UUID postId);

    /**
     * 작성자의 인스타그램 동의 중인 게시물을 삭제된 것까지 잠그고 조회한다. 탈퇴 시 동의를 철회할 대상이다.
     */
    List<Post> findInstagramAgreedByAuthorIdForUpdate(UUID authorId);

    Optional<Post> findByIdForUpdate(UUID postId);

    /**
     * 지금까지의 변경을 즉시 반영한다. 활성 게시물을 가리는 부분 유니크 인덱스가 있어, 새 게시물을 저장하기 전에 기존 게시물의 거절 처리가
     * 먼저 DB에 닿아야 한다.
     */
    void flush();

    Optional<Post> findVisibleById(UUID postId);

    /**
     * 공개 게시물에 더해 조회자가 작성자 본인이면 승인 대기 게시물도 조회한다. 작성자가 수정 전에 현재 값을 읽을 수 있어야 한다.
     */
    Optional<Post> findVisibleByIdForViewer(UUID postId, UUID viewerId);

    Optional<Post> findVisibleByIdForShare(UUID postId);

    PostSlice findVisibleRecentByAuthorId(
            UUID authorId,
            int page,
            int pageSize
    );

    Optional<Post> findValidatingByPostImageUploadIdForUpdate(UUID postImageUploadId);

    List<Post> findCalendarPostsByAuthorIdAndTopicDateBetween(
            UUID authorId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<YearMonth> findCalendarMonthsByAuthorId(UUID authorId);

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
