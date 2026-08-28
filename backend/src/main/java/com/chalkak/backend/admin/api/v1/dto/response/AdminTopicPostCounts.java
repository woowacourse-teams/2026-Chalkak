package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminTopicDetail;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주제별 게시물 검수 상태 통계")
public record AdminTopicPostCounts(
        long total,
        long validating,
        long pending,
        long approved,
        long rejected
) {

    public static AdminTopicPostCounts from(AdminTopicDetail.PostCounts counts) {
        return new AdminTopicPostCounts(
                counts.total(),
                counts.validating(),
                counts.pending(),
                counts.approved(),
                counts.rejected()
        );
    }
}
