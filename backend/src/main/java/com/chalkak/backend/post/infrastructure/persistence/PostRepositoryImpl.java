package com.chalkak.backend.post.infrastructure.persistence;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.post.repository.PostSlice;
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

    /**
     * 거절된 게시물은 중복으로 세지 않는다. 이미지 처리에서 거절당한 사용자가 올바른 사진으로 다시 올릴
     * 수 있어야 하는데, 게시물 삭제 API가 없어 한 번 거절되면 복구할 방법이 없기 때문이다.
     */
    @Override
    public boolean existsActiveByAuthorIdAndTopicId(UUID authorId, UUID topicId) {
        return postJpaRepository.existsByAuthorIdAndTopicId(
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
    public Optional<Post> findValidatingByOriginalStorageKey(String originalStorageKey) {
        return postJpaRepository.findByOriginalStorageKey(
                originalStorageKey,
                ModerationStatus.VALIDATING
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
}
