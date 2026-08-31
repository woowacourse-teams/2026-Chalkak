package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.admin.service.AdminTopicSort;
import com.chalkak.backend.topic.domain.TopicPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record AdminTopicListRequest(
        @Schema(description = "Clock 기준 주제 공개 단계")
        TopicPhase phase,

        @Schema(description = "주제 날짜 조회 시작값(포함)", example = "2026-08-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateFrom,

        @Schema(description = "주제 날짜 조회 종료값(포함)", example = "2026-08-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateTo,

        @Schema(
                description = "주제 정렬",
                defaultValue = "topicDateDesc",
                implementation = String.class,
                allowableValues = {
                        "topicDateDesc",
                        "topicDateAsc",
                        "createdAtDesc",
                        "createdAtAsc"
                }
        )
        AdminTopicSort sort,

        @Schema(description = "페이지 번호", defaultValue = "1")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        Integer page,

        @Schema(description = "페이지당 주제 수", defaultValue = "20")
        @Min(value = 1, message = "조회 조건이 올바르지 않습니다.")
        @Max(value = 100, message = "조회 조건이 올바르지 않습니다.")
        Integer pageSize
) {

    public AdminTopicListRequest {
        sort = sort == null ? AdminTopicSort.TOPIC_DATE_DESC : sort;
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
}
