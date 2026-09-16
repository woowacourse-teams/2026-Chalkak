package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.admin.repository.AdminFeedbackQueryPage;
import com.chalkak.backend.admin.repository.AdminFeedbackQueryRepository;
import com.chalkak.backend.admin.repository.AdminFeedbackQuerySort;
import com.chalkak.backend.admin.repository.AdminFeedbackSummaryProjection;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

    private static final UUID FEEDBACK_ID =
            UUID.fromString("0198fd00-0000-7000-8000-000000000003");
    private static final UUID USER_ID =
            UUID.fromString("0198fd00-0000-7000-8000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-09-16T01:00:00Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-09-16T02:00:00Z");

    @Mock
    private AdminFeedbackQueryRepository adminFeedbackQueryRepository;

    @InjectMocks
    private AdminFeedbackQueryService adminFeedbackQueryService;

    @Test
    @DisplayName("탈퇴한 작성자의 피드백은 WITHDRAWN 상태로 내려준다")
    void getFeedbacks_withdrawnWriter_derivesWithdrawnStatus() {
        // Given
        given(adminFeedbackQueryRepository.findFeedbacks(
                AdminFeedbackQuerySort.CREATED_AT_DESC, 1, 20))
                .willReturn(page(UserStatus.ACTIVE, WITHDRAWN_AT));

        // When
        AdminFeedbackListResult result = adminFeedbackQueryService.getFeedbacks(
                AdminFeedbackSort.CREATED_AT_DESC, 1, 20);

        // Then
        assertThat(result.feedbacks()).singleElement()
                .extracting(feedback -> feedback.writer().status())
                .isEqualTo(AdminUserStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("정지된 작성자의 피드백은 BANNED 상태로 내려준다")
    void getFeedbacks_bannedWriter_derivesBannedStatus() {
        // Given
        given(adminFeedbackQueryRepository.findFeedbacks(
                AdminFeedbackQuerySort.CREATED_AT_ASC, 1, 20))
                .willReturn(page(UserStatus.BANNED, null));

        // When
        AdminFeedbackListResult result = adminFeedbackQueryService.getFeedbacks(
                AdminFeedbackSort.CREATED_AT_ASC, 1, 20);

        // Then
        assertThat(result.feedbacks()).singleElement()
                .extracting(feedback -> feedback.writer().status())
                .isEqualTo(AdminUserStatus.BANNED);
    }

    @ParameterizedTest
    @CsvSource({"0, 20", "1, 0", "1, 101"})
    @DisplayName("페이지 조건이 범위를 벗어나면 조회하지 않는다")
    void getFeedbacks_invalidPagination_throwsBusinessException(int page, int pageSize) {
        assertThatThrownBy(() -> adminFeedbackQueryService.getFeedbacks(
                AdminFeedbackSort.CREATED_AT_DESC, page, pageSize))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 조건이 올바르지 않습니다.");

        then(adminFeedbackQueryRepository).shouldHaveNoInteractions();
    }

    private AdminFeedbackQueryPage page(UserStatus userStatus, Instant userDeletedAt) {
        return new AdminFeedbackQueryPage(
                List.of(new AdminFeedbackSummaryProjection(
                        FEEDBACK_ID,
                        "사진 업로드가 느려요.",
                        CREATED_AT,
                        USER_ID,
                        "user@example.com",
                        userStatus,
                        "1.2.3",
                        userDeletedAt)),
                1,
                20,
                false);
    }
}
