package com.chalkak.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.photo.domain.Photo;
import com.chalkak.backend.topic.domain.ParticipationPeriod;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PostTest {

    private static final UUID AUTHOR_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final UUID OTHER_USER_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a2");
    private static final UUID UPLOAD_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570f1");
    private static final Instant DELETED_AT = Instant.parse("2026-08-20T01:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-20T00:30:00Z");

    private final User author = UserFixture.create(AUTHOR_ID);
    private final Topic topic = Topic.create(
            "게시물 주제",
            LocalDate.of(
                    2026,
                    8,
                    20
            ),
            new ParticipationPeriod(
                    Instant.parse("2026-08-19T15:00:00Z"),
                    Instant.parse("2026-08-20T15:00:00Z")
            ),
            Instant.parse("2026-08-19T00:00:00Z")
    );
    private final Photo photo = Photo.createPhoto("chalkak/posts/dev/original/upload-id.png");

    @Test
    @DisplayName("작성자와 주제와 사진으로 검수 중인 게시물을 생성한다")
    void createPost_validValues_createsValidatingPost() {
        // When
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "  오늘의  기록  ",
                null
        );

        // Then
        assertThat(post.getAuthor()).isSameAs(author);
        assertThat(post.getTopic()).isSameAs(topic);
        assertThat(post.getPhoto()).isSameAs(photo);
        assertThat(post.getTitle()).isEqualTo("오늘의  기록");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.VALIDATING);
    }

    @Test
    @DisplayName("입력한 설명을 담아 게시물을 생성한다")
    void createPost_description_createsPostWithDescription() {
        // When
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "오늘의 기록",
                "첫 줄\n둘째 줄"
        );

        // Then
        assertThat(post.getDescription()).isEqualTo("첫 줄\n둘째 줄");
    }

    @Test
    @DisplayName("설명이 null이면 설명 없는 게시물로 생성한다")
    void createPost_nullDescription_createsPostWithoutDescription() {
        // When
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "오늘의 기록",
                null
        );

        // Then
        assertThat(post.getDescription()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "　　", "　　　　　　　　　　　"})
    @DisplayName("null, 빈 문자열, 공백 제목은 제목 없는 게시물로 생성한다")
    void createPost_blankTitle_normalizesTitleToNull(String title) {
        // When
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                title,
                null
        );

        // Then
        assertThat(post.getTitle()).isNull();
    }

    @Test
    @DisplayName("제목이 10자를 초과하면 게시물을 생성할 수 없다")
    void createPost_tooLongTitle_throwsBusinessException() {
        // Given
        String title = "12345678901";

        // When & Then
        assertThatThrownBy(() -> Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                title,
                null
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("제목은 10자 이하여야 합니다.");
    }

    @Test
    @DisplayName("작성자는 참여 기간 중인 검수 대기 게시물의 제목을 수정한다")
    void updateTitleAndDescription_pendingPostDuringOpenPeriod_updatesTitle() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "기존 제목",
                null
        );
        post.requestModeration();

        // When
        post.updateTitleAndDescription(
                AUTHOR_ID,
                "  1234567890  ",
                null,
                UPDATED_AT
        );

        // Then
        assertThat(post.getTitle()).isEqualTo("1234567890");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
    }

    @Test
    @DisplayName("작성자는 참여 기간 중인 승인 게시물의 제목을 수정한다")
    void updateTitleAndDescription_approvedPostDuringOpenPeriod_updatesTitle() {
        // Given
        Post post = createPendingPostDuringOpenPeriod("기존 제목");
        post.approve(Instant.parse("2026-08-20T00:10:00Z"));

        // When
        post.updateTitleAndDescription(
                AUTHOR_ID,
                "수정 제목",
                null,
                UPDATED_AT
        );

        // Then
        assertThat(post.getTitle()).isEqualTo("수정 제목");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(post.getModeratedAt()).isEqualTo(Instant.parse("2026-08-20T00:10:00Z"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "　　"})
    @DisplayName("null, 빈 문자열, 공백 제목은 제목 없음으로 수정한다")
    void updateTitleAndDescription_emptyTitle_normalizesTitleToNull(String title) {
        // Given
        Post post = createPendingPostDuringOpenPeriod("기존 제목");

        // When
        post.updateTitleAndDescription(
                AUTHOR_ID,
                title,
                null,
                UPDATED_AT
        );

        // Then
        assertThat(post.getTitle()).isNull();
    }

    @Test
    @DisplayName("제목을 수정할 때 Unicode code point 기준 10자를 초과하면 거부한다")
    void updateTitleAndDescription_tooLongNormalizedTitle_throwsBusinessException() {
        // Given
        Post post = createPendingPostDuringOpenPeriod("기존 제목");

        // When & Then
        assertThatThrownBy(() -> post.updateTitleAndDescription(
                AUTHOR_ID,
                "  " + "📸".repeat(11) + "  ",
                null,
                UPDATED_AT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("제목은 10자 이하여야 합니다.");
        assertThat(post.getTitle()).isEqualTo("기존 제목");
    }

    @Test
    @DisplayName("제목과 설명을 함께 교체한다")
    void updateTitleAndDescription_newValues_replacesTitleAndDescription() {
        // Given
        Post post = createPendingPostDuringOpenPeriod("기존 제목", "기존 설명");

        // When
        post.updateTitleAndDescription(
                AUTHOR_ID,
                "수정 제목",
                "새 설명",
                UPDATED_AT
        );

        // Then
        assertThat(post.getTitle()).isEqualTo("수정 제목");
        assertThat(post.getDescription()).isEqualTo("새 설명");
    }

    @Test
    @DisplayName("설명을 null로 수정하면 기존 설명을 삭제한다")
    void updateTitleAndDescription_nullDescription_clearsExistingDescription() {
        // Given
        Post post = createPendingPostDuringOpenPeriod("기존 제목", "기존 설명");

        // When
        post.updateTitleAndDescription(
                AUTHOR_ID,
                "기존 제목",
                null,
                UPDATED_AT
        );

        // Then
        assertThat(post.getDescription()).isNull();
    }

    @Test
    @DisplayName("작성자가 아닌 사용자는 게시물을 수정할 수 없다")
    void updateTitleAndDescription_otherUser_throwsForbiddenException() {
        // Given
        Post post = createPendingPostDuringOpenPeriod("기존 제목");

        // When & Then
        assertThatThrownBy(() -> post.updateTitleAndDescription(
                OTHER_USER_ID,
                "수정 제목",
                null,
                UPDATED_AT
        ))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN))
                .hasMessage("본인의 게시물만 접근할 수 있습니다.");
        assertThat(post.getTitle()).isEqualTo("기존 제목");
    }

    @ParameterizedTest
    @EnumSource(value = ModerationStatus.class, names = {"VALIDATING", "REJECTED"})
    @DisplayName("수정할 수 없는 검수 상태의 게시물은 수정할 수 없다")
    void updateTitleAndDescription_uneditableStatus_throwsBusinessException(
            ModerationStatus status) {
        // Given
        Post post = createPostWithStatus(status);

        // When & Then
        assertThatThrownBy(() -> post.updateTitleAndDescription(
                AUTHOR_ID,
                "수정 제목",
                null,
                UPDATED_AT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("현재 상태의 게시물은 수정할 수 없습니다.");
        assertThat(post.getTitle()).isEqualTo("제목");
    }

    @Test
    @DisplayName("주제 참여 기간이 종료되면 게시물을 수정할 수 없다")
    void updateTitleAndDescription_closedTopic_throwsBusinessException() {
        // Given
        Topic topic = Topic.create(
                "종료된 주제",
                LocalDate.of(
                        2026,
                        8,
                        20
                ),
                new ParticipationPeriod(
                        Instant.parse("2026-08-19T15:00:00Z"),
                        UPDATED_AT
                ),
                Instant.parse("2026-08-19T00:00:00Z")
        );
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "기존 제목",
                null
        );
        post.requestModeration();

        // When & Then
        assertThatThrownBy(() -> post.updateTitleAndDescription(
                AUTHOR_ID,
                "수정 제목",
                null,
                UPDATED_AT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("참여 기간이 종료된 게시물은 수정할 수 없습니다.");
        assertThat(post.getTitle()).isEqualTo("기존 제목");
    }

    @Test
    @DisplayName("이미지 처리가 끝나면 관리자 검수 대기 상태가 된다")
    void requestModeration_validatingPost_becomesPending() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );

        // When
        post.requestModeration();

        // Then
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
        assertThat(post.getModeratedAt()).isNull();
    }

    @Test
    @DisplayName("관리자 검수 대기 게시물을 승인하면 APPROVED가 되고 검수 시각을 기록한다")
    void approve_pendingPost_becomesApproved() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.requestModeration();
        Instant moderatedAt = Instant.parse("2026-08-20T00:00:00Z");

        // When
        post.approve(moderatedAt);

        // Then
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(post.getModeratedAt()).isEqualTo(moderatedAt);
    }

    @Test
    @DisplayName("관리자 검수 대기 게시물을 거절하면 REJECTED가 되고 검수 시각을 기록한다")
    void reject_pendingPost_becomesRejected() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.requestModeration();
        Instant moderatedAt = Instant.parse("2026-08-20T00:00:00Z");

        // When
        post.reject(moderatedAt);

        // Then
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(post.getModeratedAt()).isEqualTo(moderatedAt);
    }

    @Test
    @DisplayName("이미지 처리에 실패하면 관리자 결정 시각 없이 REJECTED가 된다")
    void failImageProcessing_validatingPost_becomesRejectedWithoutModerationTime() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );

        // When
        post.failImageProcessing();

        // Then
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(post.getModeratedAt()).isNull();
    }

    @Test
    @DisplayName("이미지 처리 중인 게시물은 관리자가 승인할 수 없다")
    void approve_validatingPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );

        // When & Then
        assertThatThrownBy(() -> post.approve(Instant.parse("2026-08-20T00:00:00Z")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 상태를 변경할 수 없습니다.");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.VALIDATING);
    }

    @Test
    @DisplayName("이미지 처리 중인 게시물은 관리자가 거절할 수 없다")
    void reject_validatingPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );

        // When & Then
        assertThatThrownBy(() -> post.reject(Instant.parse("2026-08-20T00:00:00Z")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 상태를 변경할 수 없습니다.");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.VALIDATING);
    }

    @Test
    @DisplayName("관리자 검수 대기 이후에는 이미지 실패 상태로 바꿀 수 없다")
    void failImageProcessing_pendingPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.requestModeration();

        // When & Then
        assertThatThrownBy(post::failImageProcessing)
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 상태를 변경할 수 없습니다.");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
    }

    @Test
    @DisplayName("이미 관리자 검수 대기인 게시물은 검수 요청을 반복할 수 없다")
    void requestModeration_pendingPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.requestModeration();

        // When & Then
        assertThatThrownBy(post::requestModeration)
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 상태를 변경할 수 없습니다.");
    }

    @Test
    @DisplayName("이미 검수가 끝난 게시물은 상태를 다시 변경할 수 없다")
    void approve_rejectedPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.requestModeration();
        post.reject(Instant.parse("2026-08-20T00:00:00Z"));

        // When & Then
        assertThatThrownBy(() -> post.approve(Instant.parse("2026-08-20T00:01:00Z")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 상태를 변경할 수 없습니다.");
    }

    @Test
    @DisplayName("관리자 검수 시각이 없으면 승인할 수 없다")
    void approve_withoutModeratedAt_throwsBusinessException() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.requestModeration();

        // When & Then
        assertThatThrownBy(() -> post.approve(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 시각이 필요합니다.");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
    }

    @Test
    @DisplayName("관리자 검수 시각이 없으면 거절할 수 없다")
    void reject_withoutModeratedAt_throwsBusinessException() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.requestModeration();

        // When & Then
        assertThatThrownBy(() -> post.reject(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("게시물 검수 시각이 필요합니다.");
        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
    }

    @Test
    @DisplayName("작성자는 검수 대기 게시물을 삭제할 수 있다")
    void deleteByAuthor_pendingPost_softDeletesPost() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.requestModeration();

        // When
        post.deleteByAuthor(AUTHOR_ID, DELETED_AT);

        // Then
        assertThat(post.getDeletedAt()).isEqualTo(DELETED_AT);
    }

    @Test
    @DisplayName("작성자는 승인된 게시물을 사진과 함께 삭제할 수 있다")
    void deleteByAuthor_approvedPost_softDeletesPostAndPhoto() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.requestModeration();
        post.approve(Instant.parse("2026-08-20T00:00:00Z"));

        // When
        post.deleteByAuthor(AUTHOR_ID, DELETED_AT);

        // Then
        assertThat(post.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(post.getPhoto().getDeletedAt()).isEqualTo(DELETED_AT);
    }

    @Test
    @DisplayName("작성자가 삭제를 반복해도 최초 삭제 시각을 유지한다")
    void deleteByAuthor_alreadyDeletedPost_keepsFirstDeletionTime() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.requestModeration();
        post.deleteByAuthor(AUTHOR_ID, DELETED_AT);

        // When
        post.deleteByAuthor(AUTHOR_ID, DELETED_AT.plusSeconds(60));

        // Then
        assertThat(post.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(post.getPhoto().getDeletedAt()).isEqualTo(DELETED_AT);
    }

    @Test
    @DisplayName("작성자가 아닌 사용자는 게시물을 삭제할 수 없다")
    void deleteByAuthor_otherUser_throwsForbiddenException() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.requestModeration();

        // When & Then
        assertThatThrownBy(() -> post.deleteByAuthor(OTHER_USER_ID, DELETED_AT))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FORBIDDEN))
                .hasMessage("본인의 게시물만 접근할 수 있습니다.");
        assertThat(post.getDeletedAt()).isNull();
        assertThat(post.getPhoto().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("작성자는 거절된 게시물을 삭제할 수 없다")
    void deleteByAuthor_rejectedPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        post.failImageProcessing();

        // When & Then
        assertThatThrownBy(() -> post.deleteByAuthor(AUTHOR_ID, DELETED_AT))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("검수 거절된 게시물은 삭제할 수 없습니다.");
        assertThat(post.getDeletedAt()).isNull();
        assertThat(post.getPhoto().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("작성자는 이미지 처리 중인 게시물을 삭제할 수 없다")
    void deleteByAuthor_validatingPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );

        // When & Then
        assertThatThrownBy(() -> post.deleteByAuthor(AUTHOR_ID, DELETED_AT))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("이미지 처리 중인 게시물은 삭제할 수 없습니다.");
        assertThat(post.getDeletedAt()).isNull();
        assertThat(post.getPhoto().getDeletedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ModerationStatus.class, names = {"PENDING", "APPROVED", "REJECTED"})
    @DisplayName("관리자가 삭제할 수 있는 상태의 게시물은 사진과 함께 soft delete한다")
    void deleteByAdmin_deletableStatus_softDeletesPostAndPhoto(ModerationStatus status) {
        // Given
        Post post = createPostWithStatus(status);

        // When
        post.deleteByAdmin(DELETED_AT);

        // Then
        assertThat(post.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(post.getPhoto().getDeletedAt()).isEqualTo(DELETED_AT);
    }

    @Test
    @DisplayName("이미지 처리 중인 게시물은 기본 비즈니스 오류로 삭제를 거부한다")
    void deleteByAdmin_validatingPost_throwsBusinessException() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );

        // When & Then
        assertThatThrownBy(() -> post.deleteByAdmin(DELETED_AT))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.BUSINESS_ERROR));
        assertThat(post.getDeletedAt()).isNull();
        assertThat(post.getPhoto().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("이미 삭제된 게시물을 다시 삭제해도 최초 삭제 시각을 유지한다")
    void deleteByAdmin_alreadyDeletedPost_keepsFirstDeletionTime() {
        // Given
        Post post = createPostWithStatus(ModerationStatus.PENDING);
        post.deleteByAdmin(DELETED_AT);

        // When
        post.deleteByAdmin(DELETED_AT.plusSeconds(60));

        // Then
        assertThat(post.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(post.getPhoto().getDeletedAt()).isEqualTo(DELETED_AT);
    }

    @Test
    @DisplayName("작성자 식별자와 같을 때만 작성자로 판단한다")
    void isAuthoredBy_comparesAuthorId() {
        // Given
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );

        // When & Then
        assertThat(post.isAuthoredBy(AUTHOR_ID)).isTrue();
        assertThat(post.isAuthoredBy(OTHER_USER_ID)).isFalse();
    }

    private Post createPostWithStatus(ModerationStatus status) {
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                "제목",
                null
        );
        if (status == ModerationStatus.VALIDATING) {
            return post;
        }
        if (status == ModerationStatus.REJECTED) {
            post.failImageProcessing();
            return post;
        }
        post.requestModeration();
        if (status == ModerationStatus.APPROVED) {
            post.approve(Instant.parse("2026-08-20T00:00:00Z"));
        }
        return post;
    }

    private Post createPendingPostDuringOpenPeriod(String title) {
        return createPendingPostDuringOpenPeriod(title, null);
    }

    private Post createPendingPostDuringOpenPeriod(String title, String description) {
        Post post = Post.createPost(
                author,
                topic,
                photo,
                UPLOAD_ID,
                title,
                description
        );
        post.requestModeration();
        return post;
    }
}
