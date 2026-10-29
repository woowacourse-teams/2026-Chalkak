package com.chalkak.backend.appversion.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Arrays;
import java.util.regex.Pattern;

@Embeddable
public record MinimumSupportedVersion(
        @Column(name = "minimum_supported_version", nullable = false, columnDefinition = "text") String value
) {

    private static final Pattern FORMAT = Pattern
            .compile("(0|[1-9][0-9]{0,9})\\.(0|[1-9][0-9]{0,9})\\.(0|[1-9][0-9]{0,9})");

    public MinimumSupportedVersion {
        if (value == null || !FORMAT.matcher(value).matches()
                || Arrays.stream(value.split("\\."))
                        .anyMatch(part -> Long.parseLong(part) > Integer.MAX_VALUE)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "최소 지원 버전은 앞자리 0 없이 0~2147483647 범위의 major.minor.patch여야 합니다.");
        }
    }
}
