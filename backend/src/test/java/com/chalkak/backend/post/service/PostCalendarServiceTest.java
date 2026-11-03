package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.photo.domain.Photo;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.topic.domain.ParticipationPeriod;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostCalendarServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID APPROVED_POST_ID = UUID
            .fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID PENDING_POST_ID = UUID
            .fromString("00000000-0000-0000-0000-000000000302");
    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    private static final String APPROVED_KEY = "chalkak/dev/posts/thumbnail/approved.webp";
    private static final String PENDING_KEY = "chalkak/dev/posts/thumbnail/pending.webp";

    @Mock
    private UserRepository users;

    @Mock
    private PostRepository posts;

    @Mock
    private ImageUrlProvider imageUrlProvider;

    @InjectMocks
    private PostQueryService service;

    @Test
    @DisplayName("월의 양 끝 날짜로 조회하고 승인 대기와 승인 게시물의 사진 및 상태를 반환한다")
    void getMyPostCalendar_validYearMonth_returnsCalendarResult() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(posts.findCalendarPostsByAuthorIdAndTopicDateBetween(
                USER_ID,
                MONTH.atDay(1),
                MONTH.atEndOfMonth()
        )).willReturn(List.of(
                post(
                        APPROVED_POST_ID,
                        MONTH.atDay(1),
                        ModerationStatus.APPROVED,
                        APPROVED_KEY
                ),
                post(
                        PENDING_POST_ID,
                        MONTH.atEndOfMonth(),
                        ModerationStatus.PENDING,
                        PENDING_KEY
                )
        ));
        given(imageUrlProvider.getUrl(APPROVED_KEY))
                .willReturn("https://cdn.example.com/posts/approved.webp");
        given(imageUrlProvider.getUrl(PENDING_KEY))
                .willReturn("https://cdn.example.com/posts/pending.webp");

        // When
        PostCalendarResult result = service.getMyPostCalendar(USER_ID, MONTH);

        // Then
        assertThat(result).isEqualTo(new PostCalendarResult(
                2026,
                8,
                List.of(
                        new PostCalendarResult.PostSummary(
                                MONTH.atDay(1),
                                APPROVED_POST_ID,
                                "https://cdn.example.com/posts/approved.webp",
                                ModerationStatus.APPROVED
                        ),
                        new PostCalendarResult.PostSummary(
                                MONTH.atEndOfMonth(),
                                PENDING_POST_ID,
                                "https://cdn.example.com/posts/pending.webp",
                                ModerationStatus.PENDING
                        )
                )
        ));
    }

    @Test
    @DisplayName("월별 조회 결과가 없으면 요청한 연월의 빈 목록을 반환한다")
    void getMyPostCalendar_noPosts_returnsEmptyResult() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(posts.findCalendarPostsByAuthorIdAndTopicDateBetween(
                USER_ID,
                MONTH.atDay(1),
                MONTH.atEndOfMonth()
        )).willReturn(List.of());

        // When
        PostCalendarResult result = service.getMyPostCalendar(USER_ID, MONTH);

        // Then
        assertThat(result).isEqualTo(new PostCalendarResult(
                2026,
                8,
                List.of()
        ));
    }

    @Test
    @DisplayName("인증할 수 없는 사용자의 월별 조회는 게시물을 조회하지 않고 거절한다")
    void getMyPostCalendar_unknownUser_throwsUnauthorizedException() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getMyPostCalendar(USER_ID, MONTH))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessage("유효하지 않은 인증 정보입니다.");
        then(posts).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("조회된 기록 연월을 순서대로 반환한다")
    void getMyPostCalendarMonths_validUser_returnsRecordedMonths() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(posts.findCalendarMonthsByAuthorId(USER_ID)).willReturn(List.of(MONTH));

        // When
        List<YearMonth> result = service.getMyPostCalendarMonths(USER_ID);

        // Then
        assertThat(result).containsExactly(MONTH);
    }

    @Test
    @DisplayName("기록이 없으면 현재 달을 추가하지 않고 빈 연월 목록을 반환한다")
    void getMyPostCalendarMonths_noPosts_returnsEmpty() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.of(UserFixture.create(USER_ID)));
        given(posts.findCalendarMonthsByAuthorId(USER_ID)).willReturn(List.of());

        // When
        List<YearMonth> result = service.getMyPostCalendarMonths(USER_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("미존재 또는 탈퇴로 조회할 수 없는 사용자의 연월 요청을 거절한다")
    void getMyPostCalendarMonths_unavailableUser_throwsUnauthorizedException() {
        // Given
        given(users.findActiveById(USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getMyPostCalendarMonths(USER_ID))
                .isInstanceOfSatisfying(UnauthorizedException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UNAUTHORIZED));
        then(posts).shouldHaveNoInteractions();
    }

    private Post post(
            UUID id,
            LocalDate date,
            ModerationStatus status,
            String thumbnailKey
    ) {
        Instant startsAt = date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        Topic topic = Topic.create(
                "캘린더 주제",
                date,
                new ParticipationPeriod(startsAt, startsAt.plusSeconds(86400)),
                startsAt.minusSeconds(1)
        );
        Photo photo = Photo.createPhoto("posts/original/" + id);
        photo.completeProcessing(thumbnailKey, null);
        Post post = Post.createPost(
                UserFixture.create(USER_ID),
                topic,
                photo,
                id,
                null,
                null
        );
        post.requestModeration();
        if (status == ModerationStatus.APPROVED) {
            post.approve(startsAt);
        }
        ReflectionTestUtils.setField(
                post,
                "id",
                id
        );
        return post;
    }
}
