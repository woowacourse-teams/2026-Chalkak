package com.chalkak.backend.appversion.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;

public record ChangeReason(String value) {
    private static final int MAX_LENGTH = 500;

    public ChangeReason {
        value = value == null ? "" : value.strip();
        if (value.isBlank() || value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "변경 사유는 앞뒤 공백을 제외하고 1~500자여야 합니다.");
        }
    }
}
