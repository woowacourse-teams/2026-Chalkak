package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.service.PostUpdateResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record PostUpdateResponse(
        UUID postId,

        @Schema(nullable = true)
        String title,

        @Schema(nullable = true)
        String description,

        @Schema(description = "공식 인스타그램 게시 동의 여부")
        boolean instagramUploadAgreed,

        @Schema(description = "정규화해 저장한 태그용 인스타그램 ID. 없으면 null", nullable = true)
        String instagramId
) {

    public static PostUpdateResponse from(PostUpdateResult result) {
        return new PostUpdateResponse(
                result.postId(),
                result.title(),
                result.description(),
                result.instagramUploadAgreed(),
                result.instagramId()
        );
    }
}
