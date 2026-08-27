package com.chalkak.backend.post.infrastructure.persistence;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.post.repository.PostSlice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private static final Set<String> DUPLICATE_CONSTRAINT_NAMES = Set.of(
            "ux_posts_user_topic_active",
            "ux_posts_photo_id",
            "ux_photos_original_storage_key"
    );
    private static final Set<ModerationStatus> CALENDAR_MODERATION_STATUSES = Set.of(
            ModerationStatus.APPROVED
    );

    private final PostJpaRepository postJpaRepository;

    @Override
    public Post save(Post post) {
        try {
            return postJpaRepository.saveAndFlush(post);
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateConstraint(exception)) {
                throw exception;
            }
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이미 사용된 게시물 생성 정보입니다."
            );
        }
    }

    @Override
    public void flush() {
        postJpaRepository.flush();
    }

    @Override
    public Optional<Post> findActiveByAuthorIdAndTopicIdForUpdate(UUID authorId, UUID topicId) {
        return postJpaRepository.findByAuthorIdAndTopicIdForUpdate(
                authorId,
                topicId,
                ModerationStatus.REJECTED
        );
    }

    @Override
    public Optional<Post> findVisibleById(UUID postId) {
        return postJpaRepository.findVisibleById(postId, ModerationStatus.APPROVED);
    }

    @Override
    public Optional<Post> findValidatingByPostImageUploadIdForUpdate(UUID postImageUploadId) {
        return postJpaRepository.findByPostImageUploadIdForUpdate(
                postImageUploadId,
                ModerationStatus.VALIDATING
        );
    }

    @Override
    public List<Post> findCalendarPostsByAuthorIdAndTopicDateBetween(
            UUID authorId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return postJpaRepository.findCalendarPostsByAuthorIdAndTopicDateBetween(
                authorId,
                startDate,
                endDate,
                CALENDAR_MODERATION_STATUSES
        );
    }

    @Override
    public PostSlice findVisibleRecentByTopicId(
            UUID topicId,
            int page,
            int pageSize
    ) {
        PageRequest pageRequest = PageRequest.of(
                page,
                pageSize,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Slice<Post> result = postJpaRepository.findVisibleByTopicId(
                topicId,
                ModerationStatus.APPROVED,
                pageRequest
        );

        return new PostSlice(
                result.getContent(),
                result.hasNext()
        );
    }

    @Override
    public PostSlice findVisibleRandomByTopicId(
            UUID topicId,
            String randomSeed,
            int page,
            int pageSize
    ) {
        Slice<Post> result = postJpaRepository.findVisibleRandomByTopicId(
                topicId,
                ModerationStatus.APPROVED,
                randomSeed,
                PageRequest.of(page, pageSize)
        );

        return new PostSlice(
                result.getContent(),
                result.hasNext()
        );
    }

    @Override
    public PostSlice findVisiblePopularByTopicId(
            UUID topicId,
            int page,
            int pageSize
    ) {
        Slice<Post> result = postJpaRepository.findVisiblePopularByTopicId(
                topicId,
                ModerationStatus.APPROVED,
                PageRequest.of(page, pageSize)
        );

        return new PostSlice(
                result.getContent(),
                result.hasNext()
        );
    }

    private boolean isDuplicateConstraint(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                    && DUPLICATE_CONSTRAINT_NAMES.contains(
                    constraintViolationException.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
