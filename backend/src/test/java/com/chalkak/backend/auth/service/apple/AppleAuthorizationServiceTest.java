package com.chalkak.backend.auth.service.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.auth.domain.apple.AppleAuthorization;
import com.chalkak.backend.auth.domain.identity.SocialAccount;
import com.chalkak.backend.auth.domain.identity.SocialProvider;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.user.domain.UserFixture;
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
class AppleAuthorizationServiceTest {

    private static final UUID USER_ID = UUID.fromString("0198fc00-0000-7000-8000-000000000001");
    private static final UUID ACCOUNT_ID = UUID.fromString("0198fc00-0000-7000-8000-000000000002");
    private static final UUID AUTHORIZATION_ID = UUID
            .fromString("0198fc00-0000-7000-8000-000000000003");

    @Mock
    private AppleAuthorizationRepository appleAuthorizationRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @InjectMocks
    private AppleAuthorizationService service;

    @Test
    @DisplayName("소셜 계정의 실제 인증 객체에서 ID와 암호문 스냅샷을 만든다")
    void findAuthorizationSnapshots_appleUser_returnsSnapshot() {
        // Given
        SocialAccount account = account(SocialProvider.APPLE);
        AppleAuthorization authorization = AppleAuthorization.create(account,
                "encrypted-refresh-token");
        ReflectionTestUtils.setField(
                authorization,
                "id",
                AUTHORIZATION_ID
        );
        given(socialAccountRepository.findByUserId(USER_ID)).willReturn(Optional.of(account));
        given(appleAuthorizationRepository.findAllBySocialAccountId(ACCOUNT_ID))
                .willReturn(List.of(authorization));

        // When
        List<AppleAuthorizationSnapshot> result = service.findAuthorizationSnapshots(USER_ID);

        // Then
        assertThat(result).containsExactly(
                new AppleAuthorizationSnapshot(AUTHORIZATION_ID, "encrypted-refresh-token"));
    }

    @Test
    @DisplayName("Apple이 아닌 소셜 계정에 인증 정보가 없으면 빈 목록을 반환한다")
    void findAuthorizationSnapshots_nonAppleUser_returnsEmpty() {
        // Given
        given(socialAccountRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(account(SocialProvider.GOOGLE)));
        given(appleAuthorizationRepository.findAllBySocialAccountId(ACCOUNT_ID))
                .willReturn(List.of());

        // When
        List<AppleAuthorizationSnapshot> result = service.findAuthorizationSnapshots(USER_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("회원 존재 여부와 관계없이 소셜 계정이 없으면 인증 정보를 조회하지 않는다")
    void findAuthorizationSnapshots_noSocialAccount_returnsEmpty() {
        // Given
        given(socialAccountRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // When
        List<AppleAuthorizationSnapshot> result = service.findAuthorizationSnapshots(USER_ID);

        // Then
        assertThat(result).isEmpty();
        then(appleAuthorizationRepository).shouldHaveNoInteractions();
    }

    private SocialAccount account(SocialProvider provider) {
        SocialAccount account = SocialAccount.create(
                UserFixture.create(USER_ID),
                provider,
                "a".repeat(64)
        );
        ReflectionTestUtils.setField(
                account,
                "id",
                ACCOUNT_ID
        );
        return account;
    }
}
