package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.service.AuthorPostListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AuthorPostListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AuthorPostResponse> posts
) {

    public static AuthorPostListResponse fromAuthorPostListResult(AuthorPostListResult result) {
        return new AuthorPostListResponse(result.currentPage(), result.pageSize(), result.hasNext(),
                result.posts().stream()
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
                    AuthorPostTopicResponse.fromTopicSummary(post.topic()));
        }
    }

    public record AuthorPostTopicResponse(UUID id, String title, LocalDate topicDate) {

        private static AuthorPostTopicResponse fromTopicSummary(
                AuthorPostListResult.TopicSummary topic) {
            return new AuthorPostTopicResponse(topic.id(), topic.title(), topic.topicDate());
        }
    }
}
