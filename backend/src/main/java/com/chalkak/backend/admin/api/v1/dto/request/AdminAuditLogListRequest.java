package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.AdminAuditLogSort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public record AdminAuditLogListRequest(
        @Schema(description = "처리 관리자 ID", format = "uuid")
        UUID adminId,

        @Schema(description = "관리자 작업")
        AdminAction action,

        @Schema(description = "작업 대상 유형")
        AdminTargetType targetType,

        @Schema(description = "작업 대상 ID", format = "uuid")
        UUID targetId,

        @Schema(description = "발생 시각 조회 시작값(포함)", example = "2026-08-01T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant occurredFrom,

        @Schema(description = "발생 시각 조회 종료값(포함)", example = "2026-08-31T23:59:59Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant occurredTo,

        @Schema(
                description = "발생 시각 정렬. 같은 시각에는 로그 ID로 안정 정렬합니다.",
                defaultValue = "occurredAtDesc",
                implementation = String.class,
                allowableValues = {"occurredAtDesc", "occurredAtAsc"}
        )
        AdminAuditLogSort sort,

        @Schema(description = "페이지 번호", defaultValue = "1")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        Integer page,

        @Schema(description = "페이지당 로그 수", defaultValue = "20", maximum = "100")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        @Max(value = 100, message = "조회 조건이 올바르지 않습니다.")
        Integer pageSize
) {

    public AdminAuditLogListRequest {
        sort = sort == null ? AdminAuditLogSort.OCCURRED_AT_DESC : sort;
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
