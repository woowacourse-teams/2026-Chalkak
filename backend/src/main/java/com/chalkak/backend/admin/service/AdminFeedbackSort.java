package com.chalkak.backend.admin.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;

public enum AdminFeedbackSort {
    CREATED_AT_DESC("createdAtDesc"),
    CREATED_AT_ASC("createdAtAsc");

    private final String value;

    AdminFeedbackSort(String value) {
        this.value = value;
    }

    public static AdminFeedbackSort from(String value) {
        for (AdminFeedbackSort sort : values()) {
            if (sort.value.equals(value)) {
                return sort;
            }
        }
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "피드백 정렬 조건이 올바르지 않습니다.");
    }
}
