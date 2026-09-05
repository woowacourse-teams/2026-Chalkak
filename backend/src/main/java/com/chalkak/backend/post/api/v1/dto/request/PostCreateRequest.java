package com.chalkak.backend.post.api.v1.dto.request;

import com.chalkak.backend.post.domain.PostTitle;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PostCreateRequest(
        @Schema(
                description = "주제 ID",
                example = "0198f6c1-62ba-7d30-8b12-0f733b6570b2"
        )
        @NotNull(message = "주제 정보가 올바르지 않습니다.")
        UUID topicId,

        @Schema(
                description = "사진 업로드 ID",
                example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4"
        )
        @NotNull(message = "사진 업로드 정보가 올바르지 않습니다.")
        UUID photoUploadId,

        @Schema(
                description = "작품 제목. 앞뒤 공백 제거 후 최대 10자입니다. 생략하거나 공백뿐이면 제목 없음으로 저장합니다.",
                example = "오늘의 기록",
                nullable = true
        )
        String title
) {

    /**
     * 필드 단위 400 응답을 위한 빠른 실패. 판정 기준은 {@link PostTitle}에 위임해 도메인 불변식과 어긋나지 않게 한다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "제목은 10자 이하여야 합니다.")
    public boolean isTitleLengthValid() {
        return PostTitle.isValid(title);
    }
}
