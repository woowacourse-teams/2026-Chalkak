package com.chalkak.backend.admin.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.user.AdminUserDetailProjection;
import com.chalkak.backend.admin.repository.user.AdminUserQueryCriteria;
import com.chalkak.backend.admin.repository.user.AdminUserQueryRepository;
import com.chalkak.backend.admin.repository.user.AdminUserSort;
import com.chalkak.backend.admin.repository.user.AdminUserStatus;
import com.chalkak.backend.admin.repository.user.AdminUserSummaryProjection;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.user.domain.UserRole;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserQueryServiceTest {

    private static final UUID ACTIVE_USER_ID = UUID
            .fromString("0198fc00-0000-7000-8000-000000000001");
    private static final UUID WITHDRAWN_USER_ID = UUID
            .fromString("0198fc00-0000-7000-8000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-08-20T01:00:00Z");
    private static final Instant WITHDRAWN_AT = Instant.parse("2026-08-21T01:00:00Z");
    private static final String ORIGINAL_KEY = "signatures/service-original";
    private static final String THUMBNAIL_KEY = "signatures/service-thumbnail";

    @Mock
    private AdminUserQueryRepository repository;

    @Mock
    private ImageUrlProvider imageUrlProvider;

    @InjectMocks
    private AdminUserQueryService service;

    @Test
    @DisplayName("이메일을 정규화하고 기본 정렬로 조회한 탈퇴 회원의 파생 상태를 반환한다")
    void getUsers_withdrawnFilter_returnsDerivedStatus() {
        // Given
        AdminUserSummaryProjection user = new AdminUserSummaryProjection(
                WITHDRAWN_USER_ID,
                "withdrawn+author@chalkak.invalid",
                UserStatus.ACTIVE,
                null,
                null,
                CREATED_AT,
                WITHDRAWN_AT,
                WITHDRAWN_AT,
                0,
                0,
                0
        );
        given(repository.findUsers(
                new AdminUserQueryCriteria(
                        AdminUserStatus.WITHDRAWN,
                        "withdrawn+",
                        AdminUserSort.CREATED_AT_DESC
                ),
                1,
                20
        )).willReturn(new AdminPage<>(
                List.of(user),
                1,
                20,
                false
        ));

        // When
        AdminUserListResult result = service.getUsers(
                AdminUserStatus.WITHDRAWN,
                "  WITHDRAWN+  ",
                null,
                1,
                20
        );

        // Then
        assertThat(result.users()).singleElement().satisfies(item -> {
            assertThat(item.userId()).isEqualTo(WITHDRAWN_USER_ID);
            assertThat(item.status()).isEqualTo(AdminUserStatus.WITHDRAWN);
            assertThat(item.email()).startsWith("withdrawn+");
        });
    }

    @Test
    @DisplayName("활성 사용자 상세에 사인 이미지 URL을 조립한다")
    void getUser_activeUser_returnsSignatureImageUrls() {
        // Given
        given(repository.findUserById(ACTIVE_USER_ID))
                .willReturn(Optional.of(user(ACTIVE_USER_ID, null)));
        given(imageUrlProvider.getUrl(ORIGINAL_KEY))
                .willReturn("https://cdn.example.com/signatures/original");
        given(imageUrlProvider.getUrl(THUMBNAIL_KEY))
                .willReturn("https://cdn.example.com/signatures/thumbnail");

        // When
        AdminUserDetail result = service.getUser(ACTIVE_USER_ID);

        // Then
        assertThat(result.status()).isEqualTo(AdminUserStatus.ACTIVE);
        assertThat(result.signature().originalImageUrl())
                .isEqualTo("https://cdn.example.com/signatures/original");
        assertThat(result.signature().thumbnailImageUrl())
                .isEqualTo("https://cdn.example.com/signatures/thumbnail");
    }

    @Test
    @DisplayName("탈퇴 사용자 상세는 저장 키가 있어도 이미지 URL을 노출하지 않는다")
    void getUser_withdrawnUser_hidesRemovedSignature() {
        // Given
        given(repository.findUserById(WITHDRAWN_USER_ID))
                .willReturn(Optional.of(user(WITHDRAWN_USER_ID, WITHDRAWN_AT)));

        // When
        AdminUserDetail result = service.getUser(WITHDRAWN_USER_ID);

        // Then
        assertThat(result.status()).isEqualTo(AdminUserStatus.WITHDRAWN);
        assertThat(result.signature().originalImageUrl()).isNull();
        assertThat(result.signature().thumbnailImageUrl()).isNull();
        then(imageUrlProvider).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 사용자 상세는 찾을 수 없음 예외를 반환한다")
    void getUser_unknownUser_throwsNotFoundException() {
        // Given
        given(repository.findUserById(ACTIVE_USER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getUser(ACTIVE_USER_ID))
                .isInstanceOfSatisfying(NotFoundException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.BUSINESS_ERROR));
    }

    @Test
    @DisplayName("페이지 오프셋이 정수 범위를 넘으면 조회하지 않고 거절한다")
    void getUsers_pageOffsetOverflow_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> service.getUsers(
                null,
                null,
                AdminUserSort.CREATED_AT_DESC,
                Integer.MAX_VALUE,
                100
        )).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR));
        then(repository).shouldHaveNoInteractions();
    }

    private AdminUserDetailProjection user(UUID id, Instant deletedAt) {
        return new AdminUserDetailProjection(
                id,
                "author@example.com",
                UserStatus.ACTIVE,
                UserRole.USER,
                "1.0.0",
                null,
                ORIGINAL_KEY,
                THUMBNAIL_KEY,
                CREATED_AT,
                CREATED_AT,
                deletedAt,
                0,
                0,
                0
        );
    }
}
