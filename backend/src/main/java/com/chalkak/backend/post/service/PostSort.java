package com.chalkak.backend.post.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;

public enum PostSort {
    RECENT,
    POPULAR,
    RANDOM;

    public static PostSort from(String value) {
        if ("recent".equals(value)) {
            return RECENT;
        }
        if ("popular".equals(value)) {
            return POPULAR;
        }
        if ("random".equals(value)) {
            return RANDOM;
        }

        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "조회 조건이 올바르지 않습니다."
        );
    }
}
