package com.chalkak.backend.admin.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AdminAuditSnapshot {

    private static final int MAX_FIELD_COUNT = 50;
    private static final int MAX_KEY_LENGTH = 100;
    private static final int MAX_STRING_LENGTH = 2_000;
    private static final int MAX_INSPECTION_DEPTH = 5;
    private static final int MAX_INSPECTION_VALUE_COUNT = 100;
    private static final String INVALID_STATE_MESSAGE =
            "관리자 감사 로그 상태가 올바르지 않습니다.";
    private static final String SENSITIVE_STATE_MESSAGE =
            "민감한 정보는 관리자 감사 로그에 저장할 수 없습니다.";
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9]");
    private static final Set<String> SENSITIVE_KEY_FRAGMENTS = Set.of(
            "password",
            "passwd",
            "token",
            "secret",
            "credential",
            "authorization",
            "cookie",
            "webhook",
            "fcm",
            "storagekey",
            "image",
            "photo",
            "signature",
            "thumbnail",
            "url"
    );

    private final Map<String, Object> values;

    private AdminAuditSnapshot(Map<String, Object> values) {
        this.values = values;
    }

    public static AdminAuditSnapshot from(Map<String, ?> values) {
        validateState(values);
        Map<String, Object> normalizedValues = new LinkedHashMap<>();
        values.forEach((key, value) -> normalizedValues.put(key, normalizeScalar(value)));
        return new AdminAuditSnapshot(Collections.unmodifiableMap(normalizedValues));
    }

    public Map<String, Object> values() {
        return values;
    }

    boolean isEmpty() {
        return values.isEmpty();
    }

    Object value(String fieldName) {
        return values.get(fieldName);
    }

    boolean hasExactlyFields(Set<String> fieldNames) {
        return values.keySet().equals(fieldNames);
    }

    private static void validateState(Map<String, ?> values) {
        if (values == null || values.size() > MAX_FIELD_COUNT) {
            throw invalidStateException();
        }
        int[] inspectedValueCount = {0};
        validateNoSensitiveInformation(values, 0, inspectedValueCount);
        values.keySet().forEach(AdminAuditSnapshot::validateKey);
    }

    private static void validateNoSensitiveInformation(
            Object value,
            int depth,
            int[] inspectedValueCount
    ) {
        inspectedValueCount[0]++;
        if (depth > MAX_INSPECTION_DEPTH
                || inspectedValueCount[0] > MAX_INSPECTION_VALUE_COUNT) {
            throw invalidStateException();
        }
        if (value instanceof Map<?, ?> map) {
            validateMapEntries(map, depth, inspectedValueCount);
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(element -> validateNoSensitiveInformation(
                    element,
                    depth + 1,
                    inspectedValueCount
            ));
        }
    }

    private static void validateMapEntries(
            Map<?, ?> map,
            int depth,
            int[] inspectedValueCount
    ) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw invalidStateException();
            }
            validateKey(key);
            validateSensitiveKey(key);
            validateNoSensitiveInformation(entry.getValue(), depth + 1, inspectedValueCount);
        }
    }

    private static void validateKey(String key) {
        if (key == null
                || key.isBlank()
                || key.codePointCount(0, key.length()) > MAX_KEY_LENGTH) {
            throw invalidStateException();
        }
    }

    private static void validateSensitiveKey(String key) {
        String normalizedKey = NON_ALPHANUMERIC_PATTERN.matcher(
                key.toLowerCase(Locale.ROOT)
        ).replaceAll("");
        boolean sensitive = SENSITIVE_KEY_FRAGMENTS.stream()
                .anyMatch(normalizedKey::contains);
        if (sensitive) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, SENSITIVE_STATE_MESSAGE);
        }
    }

    private static Object normalizeScalar(Object value) {
        if (value == null || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String stringValue) {
            validateStringLength(stringValue);
            AdminAuditSensitiveValueValidator.validate(stringValue);
            return stringValue;
        }
        if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)) {
            throw invalidStateException();
        }
        if (value instanceof Float floatValue && !Float.isFinite(floatValue)) {
            throw invalidStateException();
        }
        if (isImmutableNumber(value)) {
            return value;
        }
        if (value instanceof UUID
                || value instanceof Instant
                || value instanceof LocalDate) {
            return value.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        throw invalidStateException();
    }

    private static boolean isImmutableNumber(Object value) {
        Class<?> valueType = value.getClass();
        return valueType == Byte.class
                || valueType == Short.class
                || valueType == Integer.class
                || valueType == Long.class
                || valueType == BigInteger.class
                || valueType == BigDecimal.class
                || valueType == Float.class
                || valueType == Double.class;
    }

    private static void validateStringLength(String value) {
        if (value.codePointCount(0, value.length()) > MAX_STRING_LENGTH) {
            throw invalidStateException();
        }
    }

    private static BusinessException invalidStateException() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_STATE_MESSAGE);
    }
}
