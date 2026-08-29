package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.admin.service.AdminUserSort;
import com.chalkak.backend.admin.service.AdminUserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AdminUserListRequest(
        @Schema(description = "사용자 상태", defaultValue = "ACTIVE")
        AdminUserStatus status,

        @Schema(description = "대소문자를 무시하는 이메일 부분 검색")
        @Size(max = 320, message = "이메일 검색어가 올바르지 않습니다.")
        String email,

        @Schema(
                description = "가입 시각 정렬",
                defaultValue = "createdAtDesc",
                implementation = String.class,
                allowableValues = {"createdAtDesc", "createdAtAsc"})
        AdminUserSort sort,

        @Schema(description = "페이지 번호", defaultValue = "1")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        Integer page,

        @Schema(description = "페이지당 사용자 수", defaultValue = "20")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        @Max(value = 100, message = "조회 조건이 올바르지 않습니다.")
        Integer pageSize
) {

    private static final AdminUserStatus DEFAULT_STATUS = AdminUserStatus.ACTIVE;
    private static final AdminUserSort DEFAULT_SORT = AdminUserSort.CREATED_AT_DESC;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public AdminUserListRequest {
        status = status == null ? DEFAULT_STATUS : status;
        sort = sort == null ? DEFAULT_SORT : sort;
        page = page == null ? DEFAULT_PAGE : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
