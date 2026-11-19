package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.jev.domain.JevDisposition;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public record JevHistoryRequest(
        Boolean preview,
        UUID postId,
        UUID topicId,
        JevDisposition disposition,
        @Min(0)
        @Max(10000)
        Integer page,
        @Min(1)
        @Max(100)
        Integer pageSize
) {
    public JevHistoryRequest {
        page = page == null ? 0 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
