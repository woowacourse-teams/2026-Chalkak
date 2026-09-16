package com.chalkak.backend.auth.api.v1.dto.request;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 업로드 URL 발급과 요청 형식이 갈리는 것은 Apple만 authorizationCode가 필요하기 때문이다. 그쪽은
 * 폐기용 Refresh Token을 이미 보관해 두고 시작하므로 {@link SocialSignupSignatureUploadRequest}를 그대로 쓴다.
 */
public record SocialLoginRequest(
        @Schema(
                description = "ID Token 발급 소셜 로그인 제공자",
                allowableValues = {"GOOGLE", "KAKAO", "APPLE"}
        )
        @NotNull(message = "소셜 로그인 제공자는 필수입니다.")
        SocialProvider provider,

        @NotBlank(message = "ID Token은 필수입니다.")
        String idToken,

        @Schema(description = "클라이언트가 소셜 로그인 요청 전에 생성한 원본 nonce."
                + " SDK에는 이 값의 SHA-256 소문자 hex를 전달한다")
        @NotBlank(message = "rawNonce는 필수입니다.")
        String rawNonce,

        @Schema(
                description = "Apple 로그인에서 받은 일회용 Authorization Code."
                        + " APPLE일 때만 보내며, 다른 제공자가 보내면 거절한다",
                nullable = true
        )
        String authorizationCode
) {

    @JsonIgnore
    @AssertTrue(message = "Authorization Code는 APPLE 로그인에만 필요합니다.")
    public boolean isValidAuthorizationCode() {
        if (provider == null) {
            return true;
        }
        if (provider == SocialProvider.APPLE) {
            return authorizationCode != null && !authorizationCode.isBlank();
        }
        return authorizationCode == null;
    }
}
