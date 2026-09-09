package com.chalkak.backend.post.service;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 지금 참여할 수 있는 주제에 이미 게시물을 썼는지에 대한 답. {@code isPosted}가 참이면 작성 흐름이
 * 중복으로 거절할 게시물이 이미 있다는 뜻이다.
 *
 * <p>재작성이 열려 있는 경우에는 이전 게시물의 식별자와 검수 상태를 담지 않는다. 클라이언트가 다시 쓸 수
 * 있는 상태에서 지난 게시물을 가리키지 않게 하기 위해서다.
 */
public record TodayPostStatus(
        LocalDate topicDate,
        boolean isPosted,
        UUID postId,
        ModerationStatus moderationStatus
) {

    public static TodayPostStatus notPosted(LocalDate topicDate) {
        return new TodayPostStatus(topicDate, false, null, null);
    }

    public static TodayPostStatus posted(LocalDate topicDate, Post post) {
        return new TodayPostStatus(
                topicDate,
                true,
                post.getId(),
                post.getModerationStatus()
        );
    }
}
