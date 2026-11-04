package com.chalkak.backend.admin.service.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackQueryRepository;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackSort;
import com.chalkak.backend.admin.repository.feedback.AdminFeedbackProjection;
import com.chalkak.backend.admin.repository.user.AdminUserStatus;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminFeedbackQueryServiceTest {

    private static final UUID FEEDBACK_ID = UUID.fromString("0198fd00-0000-7000-8000-000000000003");
    private static final UUID USER_ID = UUID.fromString("0198fd00-0000-7000-8000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-09-16T01:00:00Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-09-16T02:00:00Z");

    @Mock
    private AdminFeedbackQueryRepository adminFeedbackQueryRepository;

    @InjectMocks
    private AdminFeedbackQueryService adminFeedbackQueryService;

    @Test
    @DisplayName("탈퇴한 작성자의 피드백은 WITHDRAWN 상태로 내려준다")
    void getFeedbacks_withdrawnAuthor_derivesWithdrawnStatus() {
        // Given
        given(adminFeedbackQueryRepository.findFeedbacks(
                null,
                AdminFeedbackSort.CREATED_AT_DESC,
                1,
                20
        ))
                .willReturn(page(UserStatus.ACTIVE, WITHDRAWN_AT));

        // When
        AdminFeedbackListResult result = adminFeedbackQueryService.getFeedbacks(
                null,
                AdminFeedbackSort.CREATED_AT_DESC,
                1,
                20
        );

        // Then
        assertThat(result.feedbacks()).singleElement()
                .extracting(feedback -> feedback.author().status())
                .isEqualTo(AdminUserStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("정지된 작성자의 피드백은 BANNED 상태로 내려준다")
    void getFeedbacks_bannedAuthor_derivesBannedStatus() {
        // Given
        given(adminFeedbackQueryRepository.findFeedbacks(
                null,
                AdminFeedbackSort.CREATED_AT_ASC,
                1,
                20
        ))
                .willReturn(page(UserStatus.BANNED, null));

        // When
        AdminFeedbackListResult result = adminFeedbackQueryService.getFeedbacks(
                null,
                AdminFeedbackSort.CREATED_AT_ASC,
                1,
                20
        );

        // Then
        assertThat(result.feedbacks()).singleElement()
                .extracting(feedback -> feedback.author().status())
                .isEqualTo(AdminUserStatus.BANNED);
    }

    @ParameterizedTest
    @CsvSource({"0, 20", "1, 0", "1, 101", "2147483647, 100"})
    @DisplayName("페이지 조건이 범위를 벗어나면 조회하지 않는다")
    void getFeedbacks_invalidPagination_throwsBusinessException(int page, int pageSize) {
        assertThatThrownBy(() -> adminFeedbackQueryService.getFeedbacks(
                null,
                AdminFeedbackSort.CREATED_AT_DESC,
                page,
                pageSize
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 조건이 올바르지 않습니다.");

        then(adminFeedbackQueryRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("사용자 필터와 요청 페이지 정보를 저장소와 결과에 전달한다")
    void getFeedbacks_userFilter_preservesFilterAndPagination() {
        // Given
        given(adminFeedbackQueryRepository.findFeedbacks(
                USER_ID,
                AdminFeedbackSort.CREATED_AT_DESC,
                2,
                7
        )).willReturn(new AdminPage<>(
                List.of(),
                2,
                7,
                false
        ));

        // When
        AdminFeedbackListResult result = adminFeedbackQueryService.getFeedbacks(
                USER_ID,
                null,
                2,
                7
        );

        // Then
        assertThat(result.currentPage()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(7);
        assertThat(result.feedbacks()).isEmpty();
    }

    @Test
    @DisplayName("없는 관리자 문의 상세는 찾을 수 없음 예외로 거절한다")
    void getFeedback_missingFeedback_throwsNotFound() {
        // Given
        given(adminFeedbackQueryRepository.findFeedbackById(FEEDBACK_ID))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> adminFeedbackQueryService.getFeedback(FEEDBACK_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("문의를 찾을 수 없습니다.");
    }

    private AdminPage<AdminFeedbackProjection> page(UserStatus userStatus,
            Instant userDeletedAt) {
        return new AdminPage<>(
                List.of(feedback(
                        userStatus,
                        userDeletedAt,
                        null
                )),
                1,
                20,
                false
        );
    }

    private AdminFeedbackProjection feedback(
            UserStatus userStatus,
            Instant userDeletedAt,
            Instant deletedAt
    ) {
        return new AdminFeedbackProjection(
                FEEDBACK_ID,
                "사진 업로드가 느려요.",
                CREATED_AT,
                deletedAt,
                USER_ID,
                "user@example.com",
                userStatus,
                "1.2.3",
                userDeletedAt,
                null,
                null
        );
    }
}
