package com.chalkak.backend.post.api.v1.dto.request;

import com.chalkak.backend.post.service.PostSort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record PostListRequest(
        @Schema(description = "조회할 주제 날짜", example = "2026-08-12")
        @NotNull(message = "조회 조건이 올바르지 않습니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate topicDate,

        @Schema(
                description = "정렬 방식",
                defaultValue = "recent",
                allowableValues = {"recent", "popular", "random"}
        )
        PostSort sort,

        @Schema(
                description = "랜덤 정렬 결과를 유지하는 값으로 다음 페이지 요청에도 동일하게 전달",
                example = "f4c3a091",
                nullable = true
        )
        @Pattern(
                regexp = "[A-Za-z0-9_-]{1,64}",
                message = "조회 조건이 올바르지 않습니다."
        )
        String randomSeed,

        @Schema(description = "페이지 번호", defaultValue = "1", example = "1")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        Integer page,

        @Schema(description = "페이지당 게시물 수", defaultValue = "20", example = "20")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        @Max(value = 100, message = "조회 조건이 올바르지 않습니다.")
        Integer pageSize
) {

    private static final PostSort DEFAULT_SORT = PostSort.RECENT;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public PostListRequest {
        sort = sort == null ? DEFAULT_SORT : sort;
        page = page == null ? DEFAULT_PAGE : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
