package com.chalkak.backend.auth.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;

public record AppleSignupAuthorization(
        String encryptedRefreshToken
) {

    private static final int ENCRYPTED_REFRESH_TOKEN_MAX_LENGTH = 4096;

    public AppleSignupAuthorization {
        if (encryptedRefreshToken == null
                || encryptedRefreshToken.isBlank()
                || encryptedRefreshToken.length() > ENCRYPTED_REFRESH_TOKEN_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "Apple 회원가입 인증 정보가 올바르지 않습니다.");
        }
    }
}
