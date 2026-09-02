package com.chalkak.backend.admin.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;

public enum AdminPostSort {
    CREATED_AT_DESC("createdAtDesc"),
    CREATED_AT_ASC("createdAtAsc");

    private final String queryValue;

    AdminPostSort(String queryValue) {
        this.queryValue = queryValue;
    }

    public static AdminPostSort from(String value) {
        for (AdminPostSort sort : values()) {
            if (sort.queryValue.equals(value)) {
                return sort;
            }
        }
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "조회 조건이 올바르지 않습니다."
        );
    }
}
