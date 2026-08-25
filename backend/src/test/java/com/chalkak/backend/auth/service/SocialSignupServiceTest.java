package com.chalkak.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.repository.IdTokenVerifier;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.repository.SignatureImageUploadIssuer;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SocialSignupServiceTest extends IntegrationTestSupport {

    private static final String ID_TOKEN = "google-id-token";
    private static final String SUBJECT = "google-subject";

    @Autowired
    private SocialSignupService socialSignupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @MockitoBean(name = "googleIdTokenVerifier")
    private IdTokenVerifier googleIdTokenVerifier;

    @MockitoBean
    private SignatureImageUploadIssuer signatureImageUploadIssuer;

    @Test
    @DisplayName("신규 소셜 계정이 요청하면 서명 이미지 업로드 URL을 발급한다")
    void createSignatureUpload_newSocialAccount_issuesUploadUrl() {
        // Given
        given(googleIdTokenVerifier.getProvider()).willReturn(SocialProvider.GOOGLE);
        given(googleIdTokenVerifier.verify(ID_TOKEN)).willReturn(identity());
        given(signatureImageUploadIssuer.issue(any(UUID.class)))
                .willAnswer(invocation -> new SignatureImageUpload(
                        invocation.getArgument(0),
                        "https://s3.example.com/presigned",
                        300L));

        // When
        SignatureImageUpload upload = socialSignupService.createSignatureUpload(
                SocialProvider.GOOGLE,
                ID_TOKEN);

        // Then
        assertThat(upload.uploadId()).isNotNull();
        assertThat(upload.uploadUrl()).isEqualTo("https://s3.example.com/presigned");
        assertThat(upload.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    @DisplayName("이미 가입된 소셜 계정은 회원가입용 서명 업로드 URL을 발급받을 수 없다")
    void createSignatureUpload_existingSocialAccount_throwsBusinessException() {
        // Given
        given(googleIdTokenVerifier.getProvider()).willReturn(SocialProvider.GOOGLE);
        given(googleIdTokenVerifier.verify(ID_TOKEN)).willReturn(identity());
        User user = userRepository.save(UserFixture.create());
        socialAccountRepository.save(SocialAccount.create(
                user,
                SocialProvider.GOOGLE,
                SUBJECT));

        // When & Then
        assertThatThrownBy(() -> socialSignupService.createSignatureUpload(
                SocialProvider.GOOGLE,
                ID_TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 가입된 소셜 계정입니다.");
    }

    private VerifiedSocialIdentity identity() {
        return new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                SUBJECT,
                "user@chalkak.test");
    }
}
