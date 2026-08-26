package com.chalkak.backend.post.repository;

import com.chalkak.backend.post.domain.Post;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository {

    Optional<Post> findVisibleById(UUID postId);

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
