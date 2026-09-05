package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminUserQueryServiceTest extends IntegrationTestSupport {

    private static final UUID ACTIVE_USER_ID =
            UUID.fromString("0198fc00-0000-7000-8000-000000000001");
    private static final UUID WITHDRAWN_USER_ID =
            UUID.fromString("0198fc00-0000-7000-8000-000000000002");
    private static final UUID UNKNOWN_USER_ID =
            UUID.fromString("0198fc00-0000-7000-8000-000000000099");
    private static final String ORIGINAL_STORAGE_KEY = "signatures/service-original";
    private static final String THUMBNAIL_STORAGE_KEY = "signatures/service-thumbnail";
    private static final String ORIGINAL_IMAGE_URL = "https://cdn.example.com/signatures/original";
    private static final String THUMBNAIL_IMAGE_URL = "https://cdn.example.com/signatures/thumbnail";
    private static final Instant CREATED_AT = Instant.parse("2026-08-20T01:00:00Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-08-21T01:00:00Z");

    @Autowired
    private AdminUserQueryService adminUserQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    signature_thumbnail_storage_key, app_version,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, 'active-service@example.com', 'ACTIVE', ?, ?, '1.0.0', ?, ?, NULL
                ), (
                    ?, ?, 'ACTIVE', ?, NULL, NULL, ?, ?, ?
                )
                """,
                ACTIVE_USER_ID,
                ORIGINAL_STORAGE_KEY,
                THUMBNAIL_STORAGE_KEY,
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT),
                WITHDRAWN_USER_ID,
                "withdrawn+" + WITHDRAWN_USER_ID + "@chalkak.invalid",
                "withdrawn/" + WITHDRAWN_USER_ID,
                Timestamp.from(CREATED_AT.plusSeconds(60)),
                Timestamp.from(WITHDRAWN_AT),
                Timestamp.from(WITHDRAWN_AT));
    }

    @Test
    @DisplayName("탈퇴 필터로 파생 WITHDRAWN 상태 사용자를 조회한다")
    void getUsers_withdrawnFilter_returnsDerivedStatus() {
        // When
        AdminUserListResult result = adminUserQueryService.getUsers(
                AdminUserStatus.WITHDRAWN,
                "WITHDRAWN+",
                AdminUserSort.CREATED_AT_DESC,
                1,
                20);

        // Then
        assertThat(result.users()).singleElement().satisfies(user -> {
            assertThat(user.userId()).isEqualTo(WITHDRAWN_USER_ID);
            assertThat(user.status()).isEqualTo(AdminUserStatus.WITHDRAWN);
            assertThat(user.email()).startsWith("withdrawn+");
        });
    }

    @Test
    @DisplayName("활성 사용자 상세에 사인 이미지 URL을 조립한다")
    void getUser_activeUser_returnsSignatureImageUrls() {
        // Given
        given(imageUrlProvider.getUrl(ORIGINAL_STORAGE_KEY)).willReturn(ORIGINAL_IMAGE_URL);
        given(imageUrlProvider.getUrl(THUMBNAIL_STORAGE_KEY)).willReturn(THUMBNAIL_IMAGE_URL);

        // When
        AdminUserDetail result = adminUserQueryService.getUser(ACTIVE_USER_ID);

        // Then
        assertThat(result.status()).isEqualTo(AdminUserStatus.ACTIVE);
        assertThat(result.signature().originalImageUrl()).isEqualTo(ORIGINAL_IMAGE_URL);
        assertThat(result.signature().thumbnailImageUrl()).isEqualTo(THUMBNAIL_IMAGE_URL);
    }

    @Test
    @DisplayName("탈퇴 사용자 상세에서 제거된 사인 저장 키를 URL로 노출하지 않는다")
    void getUser_withdrawnUser_hidesRemovedSignature() {
        // When
        AdminUserDetail result = adminUserQueryService.getUser(WITHDRAWN_USER_ID);

        // Then
        assertThat(result.status()).isEqualTo(AdminUserStatus.WITHDRAWN);
        assertThat(result.signature().originalImageUrl()).isNull();
        assertThat(result.signature().thumbnailImageUrl()).isNull();
        verifyNoInteractions(imageUrlProvider);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 상세는 404 예외를 발생시킨다")
    void getUser_unknownUser_throwsNotFoundException() {
        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> adminUserQueryService.getUser(UNKNOWN_USER_ID));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    @DisplayName("페이지 오프셋이 정수 범위를 넘으면 잘못된 요청이다")
    void getUsers_pageOffsetOverflow_throwsBusinessException() {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminUserQueryService.getUsers(
                        null,
                        null,
                        AdminUserSort.CREATED_AT_DESC,
                        Integer.MAX_VALUE,
                        100));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }
}
