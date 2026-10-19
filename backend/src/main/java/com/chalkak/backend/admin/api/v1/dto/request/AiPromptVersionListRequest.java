package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.aireview.domain.AiPromptType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AiPromptVersionListRequest(
        @NotNull
        @Schema(description = "조회할 프롬프트 type")
        AiPromptType type,

        @Schema(defaultValue = "1", minimum = "1")
        Integer page,

        @Schema(defaultValue = "20", minimum = "1", maximum = "100")
        Integer pageSize
) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public AiPromptVersionListRequest {
        page = page == null ? DEFAULT_PAGE : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
