package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.service.PostUpdateResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record PostUpdateResponse(
        UUID postId,

        @Schema(nullable = true)
        String title
) {

    public static PostUpdateResponse from(PostUpdateResult result) {
        return new PostUpdateResponse(
                result.postId(),
                result.title()
        );
    }
}
