package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.converter.AdminUserSortConverter;
import com.chalkak.backend.admin.service.AdminUserDetail;
import com.chalkak.backend.admin.service.AdminUserListResult;
import com.chalkak.backend.admin.service.AdminUserQueryService;
import com.chalkak.backend.admin.service.AdminUserSort;
import com.chalkak.backend.admin.service.AdminUserStatus;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserController.class)
@Import({
        GlobalExceptionHandler.class,
        AdminArgumentResolverWebMvcConfig.class,
        AdminUserSortConverter.class
})
class AdminUserControllerTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("0198fd00-0000-7000-8000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("0198fd00-0000-7000-8000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-08-20T01:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-20T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserQueryService adminUserQueryService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("관리자는 기본 조건으로 전체 사용자 목록을 조회한다")
    void getUsers_withoutFilters_returnsDefaultPage() throws Exception {
        // Given
        AdminUserListResult result = new AdminUserListResult(
                1,
                20,
                false,
                List.of(new AdminUserListResult.UserSummary(
                        USER_ID,
                        "user@example.com",
                        AdminUserStatus.ACTIVE,
                        "1.2.3",
                        SocialProvider.GOOGLE,
                        new AdminUserListResult.PostCounts(3, 0, 1, 2, 0),
                        CREATED_AT,
                        UPDATED_AT,
                        null)));
        given(adminUserQueryService.getUsers(
                null,
                null,
                AdminUserSort.CREATED_AT_DESC,
                1,
                20)).willReturn(result);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.users[0].userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.users[0].email").value("user@example.com"))
                .andExpect(jsonPath("$.users[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.users[0].socialProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.users[0].postCounts.total").value(3));

        then(adminUserQueryService).should().getUsers(
                null,
                null,
                AdminUserSort.CREATED_AT_DESC,
                1,
                20);
    }

    @Test
    @DisplayName("관리자 사용자 필터와 정렬·페이지 조건을 서비스에 전달한다")
    void getUsers_withFilters_passesQueryParameters() throws Exception {
        // Given
        given(adminUserQueryService.getUsers(
                AdminUserStatus.WITHDRAWN,
                "withdrawn+",
                AdminUserSort.CREATED_AT_ASC,
                2,
                50)).willReturn(new AdminUserListResult(2, 50, false, List.of()));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/users")
                        .queryParam("status", "WITHDRAWN")
                        .queryParam("email", "withdrawn+")
                        .queryParam("sort", "createdAtAsc")
                        .queryParam("page", "2")
                        .queryParam("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isEmpty());

        then(adminUserQueryService).should().getUsers(
                AdminUserStatus.WITHDRAWN,
                "withdrawn+",
                AdminUserSort.CREATED_AT_ASC,
                2,
                50);
    }

    @ParameterizedTest
    @CsvSource({
            "status, UNKNOWN",
            "sort, unknown",
            "page, 0",
            "pageSize, 0",
            "pageSize, 101"
    })
    @DisplayName("관리자 사용자 조회 조건이 잘못되면 400을 반환한다")
    void getUsers_invalidQuery_returnsBadRequest(
            String parameterName,
            String parameterValue
    ) throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .queryParam(parameterName, parameterValue))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));

        then(adminUserQueryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("관리자는 탈퇴 사용자의 비식별 상세를 조회한다")
    void getUser_withdrawnUser_returnsDetailWithoutSignatureUrls() throws Exception {
        // Given
        AdminUserDetail result = new AdminUserDetail(
                USER_ID,
                "withdrawn+" + USER_ID + "@chalkak.invalid",
                AdminUserStatus.WITHDRAWN,
                null,
                SocialProvider.KAKAO,
                new AdminUserDetail.Signature(null, null),
                new AdminUserListResult.PostCounts(1, 0, 0, 0, 1),
                CREATED_AT,
                UPDATED_AT,
                UPDATED_AT);
        given(adminUserQueryService.getUser(USER_ID)).willReturn(result);

        // When & Then
        mockMvc.perform(get("/api/v1/admin/users/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.signature.originalImageUrl").isEmpty())
                .andExpect(jsonPath("$.signature.thumbnailImageUrl").isEmpty());
    }

    @Test
    @DisplayName("존재하지 않는 관리자 사용자 상세는 404를 반환한다")
    void getUser_unknownUser_returnsNotFound() throws Exception {
        // Given
        given(adminUserQueryService.getUser(USER_ID)).willThrow(new NotFoundException(
                ErrorCode.BUSINESS_ERROR,
                "사용자를 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(get("/api/v1/admin/users/{userId}", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }
}
