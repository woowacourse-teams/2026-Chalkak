package com.chalkak.backend.post.api.v1.dto.response;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.service.PostCalendarResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PostCalendarResponse(
        int year,
        int month,
        List<CalendarPostResponse> posts
) {

    public static PostCalendarResponse from(PostCalendarResult result) {
        return new PostCalendarResponse(
                result.year(),
                result.month(),
                result.posts().stream()
                        .map(CalendarPostResponse::from)
                        .toList()
        );
    }

    public record CalendarPostResponse(
            LocalDate topicDate,
            UUID postId,
            String thumbnailImageUrl,
            @Schema(allowableValues = {"APPROVED", "PENDING"})
            ModerationStatus status
    ) {

        private static CalendarPostResponse from(PostCalendarResult.PostSummary post) {
            return new CalendarPostResponse(
                    post.topicDate(),
                    post.postId(),
                    post.thumbnailImageUrl(),
                    post.status()
            );
        }
    }
}
