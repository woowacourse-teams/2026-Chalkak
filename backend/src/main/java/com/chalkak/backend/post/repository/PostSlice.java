package com.chalkak.backend.post.repository;

import com.chalkak.backend.post.domain.Post;
import java.util.List;

public record PostSlice(
        List<Post> posts,
        boolean hasNext
) {
}
