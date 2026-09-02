package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.service.PostImageUploadResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record PostImageUploadResponse(
        @Schema(
                description = "게시물 생성 요청에 그대로 전달할 업로드 ID",
                example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4"
        )
        UUID uploadId,

        @Schema(description = "S3 presigned PUT URL")
        String uploadUrl,

        @Schema(description = "발급 시점부터의 URL 유효 시간", example = "300")
        long expiresInSeconds,

        @Schema(
                description = "PUT 요청에 그대로 사용해야 하는 Content-Type",
                example = "image/webp"
        )
        String contentType,

        @Schema(
                description = "허용 용량. 초과분은 업로드 후 이미지 처리 단계에서 거절된다",
                example = "5242880"
        )
        long maxBytes
) {

    public static PostImageUploadResponse from(PostImageUploadResult result) {
        return new PostImageUploadResponse(
                result.uploadId(),
                result.uploadUrl(),
                result.expiresInSeconds(),
                result.contentType(),
                result.maxBytes()
        );
    }
}
