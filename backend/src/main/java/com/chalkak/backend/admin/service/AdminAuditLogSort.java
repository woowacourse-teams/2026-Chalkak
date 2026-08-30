package com.chalkak.backend.admin.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.Arrays;

public enum AdminAuditLogSort {
    OCCURRED_AT_DESC("occurredAtDesc"),
    OCCURRED_AT_ASC("occurredAtAsc");

    private final String value;

    AdminAuditLogSort(String value) {
        this.value = value;
    }

    public static AdminAuditLogSort from(String value) {
        return Arrays.stream(values())
                .filter(sort -> sort.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "감사 로그 정렬 조건이 올바르지 않습니다."
                ));
    }
}
