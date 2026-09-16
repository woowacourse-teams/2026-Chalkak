package com.chalkak.backend.auth.api.v1.dto.request;

import com.chalkak.backend.auth.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 로그인과 요청 형식이 갈리는 것은 Apple만 로그인에서 authorizationCode가 필요하기 때문이다.
 * 업로드 URL 발급은 로그인이 보관해 둔 Refresh Token을 쓰므로 code를 받지 않는다.
 */
public record SocialSignupSignatureUploadRequest(
        @Schema(
                description = "ID Token 발급 소셜 로그인 제공자",
                allowableValues = {"GOOGLE", "KAKAO", "APPLE"}
        )
        @NotNull(message = "소셜 로그인 제공자는 필수입니다.")
        SocialProvider provider,
        @NotBlank(message = "ID Token은 필수입니다.")
        String idToken,
        @Schema(description = "클라이언트가 소셜 로그인 요청 전에 생성한 원본 nonce. SDK에는 이 값의 SHA-256 소문자 hex를 전달한다")
        @NotBlank(message = "rawNonce는 필수입니다.")
        String rawNonce
) {
}
