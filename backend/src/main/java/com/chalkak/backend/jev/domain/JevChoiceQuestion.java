package com.chalkak.backend.jev.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 관리자 입력 순서를 보존하며 공급자의 Choice 요청 형식을 검증한다. */
public record JevChoiceQuestion(
        String type,
        String instructions,
        Map<String, String> criteria
) {

    private static final int MAX_OPTIONS = 255;

    public JevChoiceQuestion {
        if (!"choice".equals(type) || instructions == null || instructions.isBlank()
                || criteria == null || criteria.size() < 2 || criteria.size() > MAX_OPTIONS) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Jev Choice 질문과 선택지 설명을 입력해 주세요.");
        }
        for (Map.Entry<String, String> option : criteria.entrySet()) {
            if (option.getKey() == null || !option.getKey().matches("[a-z][a-z0-9_]{0,63}")
                    || option.getValue() == null || option.getValue().isBlank()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Jev 선택지 이름과 설명이 올바르지 않습니다.");
            }
        }
        criteria = Collections.unmodifiableMap(new LinkedHashMap<>(criteria));
    }
}
