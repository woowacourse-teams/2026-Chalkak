package com.chalkak.backend.post.api.v1.dto.request;

import com.chalkak.backend.post.domain.Post;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

@Schema(requiredProperties = "title")
public class PostUpdateRequest {

    private String title;

    @JsonIgnore
    private boolean titleProvided;

    @JsonSetter("title")
    @Schema(
            description = "수정할 제목. 앞뒤 공백을 제거하며 null 또는 공백이면 제목을 삭제합니다.",
            example = "수정한 제목",
            nullable = true,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    public void setTitle(String title) {
        this.title = title;
        this.titleProvided = true;
    }

    public String title() {
        return title;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "제목 정보가 올바르지 않습니다.")
    public boolean isTitleProvided() {
        return titleProvided;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "제목은 10자 이하여야 합니다.")
    public boolean isTitleLengthValid() {
        if (title == null) {
            return true;
        }
        String normalizedTitle = title.strip();
        return normalizedTitle.codePointCount(0, normalizedTitle.length())
                <= Post.MAX_TITLE_LENGTH;
    }
}
