package com.chalkak.backend.post.repository;

import com.chalkak.backend.post.domain.Post;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository {

    Optional<Post> findVisibleById(UUID postId);
}
