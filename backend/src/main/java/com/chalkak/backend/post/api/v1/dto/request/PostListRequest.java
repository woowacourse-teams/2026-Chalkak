package com.chalkak.backend.post.api.v1.dto.request;

import com.chalkak.backend.post.service.PostSort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record PostListRequest(
        @NotNull(message = "조회 조건이 올바르지 않습니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate topicDate,

        PostSort sort,

        @Pattern(
                regexp = "[A-Za-z0-9_-]{1,64}",
                message = "조회 조건이 올바르지 않습니다."
        )
        String randomSeed,

        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        Integer page,

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
