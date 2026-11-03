package com.chalkak.backend.post.api.v1.dto.request;

import com.chalkak.backend.post.domain.PostDescription;
import com.chalkak.backend.post.domain.PostTitle;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

@Schema(requiredProperties = "title")
public record PostUpdateRequest(
        @JsonProperty(required = true)
        @Schema(
                description = "수정할 제목. 앞뒤 공백을 제거하며 null 또는 공백이면 제목을 삭제합니다.",
                example = "수정한 제목",
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(
                description = "수정할 설명. 앞뒤 공백과 줄바꿈 제거 후 최대 300자입니다. 생략하거나 null 또는 공백이면 설명을 삭제합니다. 설명을 쓸 수 없는 이전 버전 앱이 요청해도 안전하도록 선택 필드이므로, 기존 설명을 유지하려면 현재 설명을 다시 보내야 합니다.",
                example = "수정한 설명",
                nullable = true)
        String description
) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "제목은 10자 이하여야 합니다.")
    public boolean isTitleLengthValid() {
        return PostTitle.isLengthValid(title);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "설명은 300자 이하여야 합니다.")
    public boolean isDescriptionLengthValid() {
        return PostDescription.isLengthValid(description);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "제목에 사용할 수 없는 문자가 포함되어 있습니다.")
    public boolean isTitleCharacterValid() {
        return PostTitle.isCharacterValid(title);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "설명에 사용할 수 없는 문자가 포함되어 있습니다.")
    public boolean isDescriptionCharacterValid() {
        return PostDescription.isCharacterValid(description);
    }
}
