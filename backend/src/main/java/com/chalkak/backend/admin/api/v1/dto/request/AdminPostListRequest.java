package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.admin.repository.post.AdminPostInstagramConsentFilter;
import com.chalkak.backend.admin.repository.post.AdminPostSort;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public record AdminPostListRequest(
        @Schema(description = "게시물 검수 상태")
        AdminPostListStatus status,

        @Schema(description = "주제 ID", format = "uuid")
        UUID topicId,

        @Schema(description = "작성자 사용자 ID", format = "uuid")
        UUID userId,

        @Schema(
                description = "정렬(likeCountDesc는 topicId와 함께만 사용 가능, "
                        + "좋아요 수 내림차순·등록 시각 내림차순·ID 오름차순)",
                defaultValue = "createdAtDesc",
                implementation = String.class,
                allowableValues = {
                        "createdAtDesc", "createdAtAsc", "likeCountDesc"})
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

        @Schema(hidden = true)
        String topicDate,

        @Schema(hidden = true)
        String createdAtFrom,

        @Schema(hidden = true)
        String createdAtTo,

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

    /**
     * 날짜 필터({@code topicDate}, {@code createdAtFrom}, {@code createdAtTo})는 제거했다. 이를 보내는 구버전 관리자 웹이 필터가
     * 빠진 목록을 적용된 결과로 오해하지 않도록 조용히 무시하지 않고 거절한다. 관리자 웹이 날짜 필터를 더 이상 보내지 않게
     * 배포되면(#920) 세 필드와 함께 지운다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "조회 조건이 올바르지 않습니다.")
    public boolean isRemovedDateFilterAbsent() {
        return isBlank(topicDate) && isBlank(createdAtFrom) && isBlank(createdAtTo);
    }

    public ModerationStatus moderationStatus() {
        if (status == null) {
            return null;
        }
        return status.toModerationStatus();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
