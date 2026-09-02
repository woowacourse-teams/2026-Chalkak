package com.chalkak.backend.common.util;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.UUID;

public final class CanonicalUuidParser {

    private CanonicalUuidParser() {
    }

    public static UUID parse(String value) {
        UUID uuid;

        try {
            uuid = UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw invalidUuidException();
        }

        if (!uuid.toString().equalsIgnoreCase(value)) {
            throw invalidUuidException();
        }

        return uuid;
    }

    private static BusinessException invalidUuidException() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "ID 형식이 올바르지 않습니다."
        );
    }
}
