package com.chalkak.backend.admin.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;

public enum AdminUserSort {
    CREATED_AT_DESC("createdAtDesc"),
    CREATED_AT_ASC("createdAtAsc");

    private final String value;

    AdminUserSort(String value) {
        this.value = value;
    }

    public static AdminUserSort from(String value) {
        for (AdminUserSort sort : values()) {
            if (sort.value.equals(value)) {
                return sort;
            }
        }
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "사용자 정렬 조건이 올바르지 않습니다.");
    }
}
