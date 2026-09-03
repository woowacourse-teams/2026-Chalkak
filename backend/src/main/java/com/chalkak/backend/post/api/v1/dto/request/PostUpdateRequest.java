package com.chalkak.backend.post.api.v1.dto.request;

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
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String title
) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "제목은 10자 이하여야 합니다.")
    public boolean isTitleLengthValid() {
        return PostTitle.isValid(title);
    }
}
