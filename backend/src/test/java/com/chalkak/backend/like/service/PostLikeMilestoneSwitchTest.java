package com.chalkak.backend.like.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.like.repository.PostLikeCounterRepository;
import com.chalkak.backend.like.repository.PostLikeRepository;
import com.chalkak.backend.notification.service.NotificationService;
import com.chalkak.backend.photo.domain.Photo;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.topic.domain.ParticipationPeriod;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

class PostLikeMilestoneSwitchTest {

    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static final UUID POST_ID = UUID.fromString("019a47c0-3db8-72dc-8f2d-07a15432d104");
    private static final UUID USER_ID = UUID.fromString("019a47c0-3db8-72dc-8f2d-07a15432d101");

    private final PostLikeRepository likes = mock(PostLikeRepository.class);
    private final PostLikeCounterRepository counters = mock(PostLikeCounterRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final PostRepository posts = mock(PostRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PostLikeService.class)
            .withBean(PostLikeRepository.class, () -> likes)
            .withBean(PostLikeCounterRepository.class, () -> counters)
            .withBean(NotificationService.class, () -> notifications)
            .withBean(PostRepository.class, () -> posts)
            .withBean(UserRepository.class, () -> users)
            .withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC));

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"false", "true"})
    @DisplayName("임시 스위치 기본값·OFF는 최고 단계만 갱신하고 ON만 알림을 생성한다")
    void likePost_activationSwitch_controlsCreationOnly(String enabled) {
        // Given
        var user = UserFixture.create(USER_ID);
        Post post = Post.createPost(
                user,
                Topic.create(
                        "주제",
                        LocalDate.parse("2026-11-03"),
                        new ParticipationPeriod(NOW, NOW.plusSeconds(86400)),
                        NOW.minusSeconds(86400)
                ),
                Photo.createPhoto("post/like-switch.webp"),
                UUID.randomUUID(),
                "사진",
                null
        );
        ReflectionTestUtils.setField(
                post,
                "id",
                POST_ID
        );
        post.requestModeration();
        post.approve(NOW);
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(user));
        given(posts.findVisibleByIdForShare(POST_ID)).willReturn(Optional.of(post));
        given(likes.createIfAbsent(POST_ID, USER_ID)).willReturn(1);
        given(counters.getCountByPostId(POST_ID)).willReturn(5L);
        given(counters.updateLastMilestoneCount(POST_ID, 5L)).willReturn(true);
        ApplicationContextRunner configured = runner;
        if (enabled != null) {
            configured = runner.withPropertyValues(
                    "chalkak.notification.like-milestone.enabled=" + enabled);
        }

        // When & Then
        configured.run(context -> {
            assertThat(context).hasNotFailed();
            PostLikeResult result = context.getBean(PostLikeService.class)
                    .likePost(POST_ID, USER_ID);
            assertThat(result.likeCount()).isEqualTo(5L);
            verify(counters).increment(POST_ID);
            verify(counters).updateLastMilestoneCount(POST_ID, 5L);
            if (Boolean.parseBoolean(enabled)) {
                verify(notifications).createForLikeMilestone(
                        post,
                        5L,
                        NOW
                );
                return;
            }
            verifyNoInteractions(notifications);
        });
    }
}
