package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.service.AuthorPostListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AuthorPostListResponse(List<AuthorPostResponse> posts) {

    public static AuthorPostListResponse fromAuthorPostListResult(AuthorPostListResult result) {
        return new AuthorPostListResponse(result.posts().stream()
                .map(AuthorPostResponse::fromPostSummary)
                .toList());
    }

    public record AuthorPostResponse(
            UUID id,
            String originalImageUrl,
            @Schema(nullable = true)
            String thumbnailImageUrl,
            AuthorPostTopicResponse topic
    ) {

        private static AuthorPostResponse fromPostSummary(AuthorPostListResult.PostSummary post) {
            return new AuthorPostResponse(
                    post.id(),
                    post.originalImageUrl(),
                    post.thumbnailImageUrl(),
                    new AuthorPostTopicResponse(
                            post.topic().id(),
                            post.topic().title(),
                            post.topic().topicDate()));
        }
    }

    public record AuthorPostTopicResponse(UUID id, String title, LocalDate topicDate) {
    }
}
