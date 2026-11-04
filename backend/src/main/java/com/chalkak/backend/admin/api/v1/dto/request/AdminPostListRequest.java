package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.admin.repository.post.AdminPostInstagramConsentFilter;
import com.chalkak.backend.admin.repository.post.AdminPostSort;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public record AdminPostListRequest(
        @Schema(description = "게시물 검수 상태")
        AdminPostListStatus status,

        @Schema(description = "주제 ID", format = "uuid")
        UUID topicId,

        @Schema(description = "주제 날짜", example = "2026-08-12")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate topicDate,

        @Schema(description = "작성자 사용자 ID", format = "uuid")
        UUID userId,

        @Schema(description = "등록 시각 조회 시작값(포함)", example = "2026-08-01T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdAtFrom,

        @Schema(description = "등록 시각 조회 종료값(포함)", example = "2026-08-31T23:59:59Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdAtTo,

        @Schema(
                description = "등록 시각 정렬",
                defaultValue = "createdAtDesc",
                implementation = String.class,
                allowableValues = {
                        "createdAtDesc", "createdAtAsc"})
        AdminPostSort sort,

        @Schema(
                description = "인스타그램 게시 동의 상태. 생략하면 전체. "
                        + "agreed는 현재 동의, notAgreed는 철회한 적 없는 미동의, "
                        + "revoked는 철회 후 현재 미동의",
                implementation = String.class,
                allowableValues = {
                        "agreed", "notAgreed", "revoked"})
        AdminPostInstagramConsentFilter instagramConsent,

        @Schema(
                description = "인스타그램 ID 포함 검색어. 대소문자를 구분하지 않고 "
                        + "앞뒤 공백과 맨 앞 @ 하나는 무시하며, 비어 있으면 필터하지 않음",
                example = "chalkak_official")
        String instagramId,

        @Schema(description = "페이지 번호", defaultValue = "1", example = "1")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        Integer page,

        @Schema(description = "페이지당 게시물 수", defaultValue = "20", example = "20")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        @Max(value = 100, message = "조회 조건이 올바르지 않습니다.")
        Integer pageSize
) {

    private static final AdminPostSort DEFAULT_SORT = AdminPostSort.CREATED_AT_DESC;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public AdminPostListRequest {
        sort = sort == null ? DEFAULT_SORT : sort;
        page = page == null ? DEFAULT_PAGE : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }

    /**
     * PostgreSQL 문자열 타입은 NUL(U+0000)을 받지 않아, 통과시키면 검색 쿼리 실행 시점에 500이 되므로 미리 거른다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "조회 조건이 올바르지 않습니다.")
    public boolean isInstagramIdCharacterValid() {
        return instagramId == null || instagramId.indexOf('\u0000') < 0;
    }

    public ModerationStatus moderationStatus() {
        if (status == null) {
            return null;
        }
        return status.toModerationStatus();
    }
}
