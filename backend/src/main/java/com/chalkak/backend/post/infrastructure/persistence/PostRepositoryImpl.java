package com.chalkak.backend.post.infrastructure.persistence;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.post.repository.PostSlice;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private final PostJpaRepository postJpaRepository;

    @Override
    public Optional<Post> findVisibleById(UUID postId) {
        return postJpaRepository.findVisibleById(postId, ModerationStatus.APPROVED);
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
}
