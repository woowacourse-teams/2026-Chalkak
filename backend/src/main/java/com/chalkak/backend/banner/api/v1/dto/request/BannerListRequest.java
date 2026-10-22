package com.chalkak.backend.banner.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record BannerListRequest(
        @Schema(defaultValue = "1", minimum = "1")
        Integer page,

        @Schema(defaultValue = "20", minimum = "1", maximum = "100")
        Integer pageSize
) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public BannerListRequest {
        page = page == null ? DEFAULT_PAGE : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
