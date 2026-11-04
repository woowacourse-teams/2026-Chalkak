package com.chalkak.backend.admin.repository.post;

import com.chalkak.backend.post.domain.ModerationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminPostQueryCriteria(
        ModerationStatus status,
        UUID topicId,
        LocalDate topicDate,
        UUID userId,
        Instant createdAtFrom,
        Instant createdAtTo,
        AdminPostSort sort,
        AdminPostInstagramConsentFilter instagramConsent,
        String instagramId
) {

    private static final String LIKE_ESCAPE = "\\";

    /**
     * 인스타그램 ID 포함 검색용 LIKE 패턴. {@code _}는 유효한 ID 문자이므로 LIKE 와일드카드가 되지 않게 이스케이프한다.
     * 검색어가 없으면 {@code null}이다.
     */
    public String instagramIdPattern() {
        if (instagramId == null) {
            return null;
        }
        String escaped = instagramId
                .replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }
}
