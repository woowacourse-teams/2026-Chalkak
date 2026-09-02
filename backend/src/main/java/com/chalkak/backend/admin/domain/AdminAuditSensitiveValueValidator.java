package com.chalkak.backend.admin.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.List;
import java.util.regex.Pattern;

final class AdminAuditSensitiveValueValidator {

    private static final String SENSITIVE_VALUE_MESSAGE =
            "민감한 정보는 관리자 감사 로그에 저장할 수 없습니다.";
    private static final int CASE_INSENSITIVE = Pattern.CASE_INSENSITIVE;
    private static final List<Pattern> SENSITIVE_VALUE_PATTERNS = List.of(
            Pattern.compile("\\bbearer\\s+\\S+", CASE_INSENSITIVE),
            Pattern.compile(
                    "\\b(?:password|passwd|token|access[_-]?token|refresh[_-]?token"
                            + "|id[_-]?token|api[_-]?key|secret|credential|authorization"
                            + "|cookie|webhook(?:[_-]?url)?|fcm[_-]?token"
                            + "|registration[_-]?token|device[_-]?token|push[_-]?token"
                            + "|storage[_-]?key|image[_-]?url|photo[_-]?url"
                            + "|thumbnail[_-]?url|signature[_-]?(?:key|url))"
                            + "\\s*[:=]\\s*\\S+",
                    CASE_INSENSITIVE
            ),
            Pattern.compile(
                    "https://hooks\\.slack(?:-gov)?\\.com/services/\\S+",
                    CASE_INSENSITIVE
            ),
            Pattern.compile(
                    "https://fcm\\.googleapis\\.com/fcm/send/\\S+",
                    CASE_INSENSITIVE
            ),
            Pattern.compile(
                    "(?:[A-Za-z0-9_-]{10,}:)?APA91[A-Za-z0-9_-]{20,}",
                    CASE_INSENSITIVE
            ),
            Pattern.compile(
                    "https?://[^\\s?#]+\\.(?:png|jpe?g|webp|gif|avif|heic|svg)"
                            + "(?:[?#]\\S*)?",
                    CASE_INSENSITIVE
            ),
            Pattern.compile("data:image/[^;\\s]+;base64,\\S+", CASE_INSENSITIVE),
            Pattern.compile(
                    "(?:x-amz-(?:credential|signature|security-token)"
                            + "|x-goog-signature)=\\S+",
                    CASE_INSENSITIVE
            ),
            Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}"),
            Pattern.compile(
                    "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"
            )
    );

    private AdminAuditSensitiveValueValidator() {
    }

    static void validate(String value) {
        boolean sensitiveValue = SENSITIVE_VALUE_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(value).find());
        if (sensitiveValue) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, SENSITIVE_VALUE_MESSAGE);
        }
    }
}
