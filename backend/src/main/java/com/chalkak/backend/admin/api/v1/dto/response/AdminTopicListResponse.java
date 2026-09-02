package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminTopicListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 주제 목록")
public record AdminTopicListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AdminTopicDetailResponse> topics
) {

    public static AdminTopicListResponse from(AdminTopicListResult result) {
        return new AdminTopicListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.topics().stream()
                        .map(AdminTopicDetailResponse::from)
                        .toList()
        );
    }
}
