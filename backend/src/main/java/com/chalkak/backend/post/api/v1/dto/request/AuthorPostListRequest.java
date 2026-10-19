package com.chalkak.backend.post.api.v1.dto.request;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record AuthorPostListRequest(
        @Schema(description = "조회할 게시물 수", defaultValue = "20", example = "20")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        @Max(value = 100, message = "조회 조건이 올바르지 않습니다.")
        Integer pageSize,

        @Schema(
                description = "직전 응답의 nextCursor. 생략하면 최신 사진부터 조회",
                format = "date",
                example = "2026-04-01",
                nullable = true)
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "조회 조건이 올바르지 않습니다.")
        String cursor
) {

    private static final int DEFAULT_PAGE_SIZE = 20;

    public AuthorPostListRequest {
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }

    public LocalDate toCursorDate() {
        if (cursor == null) {
            return null;
        }
        try {
            return LocalDate.parse(cursor);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "조회 조건이 올바르지 않습니다.");
        }
    }
}
