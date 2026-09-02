package com.chalkak.backend.post.api.v1.dto.request;

import com.chalkak.backend.post.domain.Post;
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
                description = "작품 제목. 공백이 아닌 제목은 최대 10자이며, 생략하거나 공백이면 제목 없음으로 저장합니다.",
                example = "오늘의 기록",
                nullable = true
        )
        String title
) {

    /**
     * 이모지 한 글자는 UTF-16 code unit 두 칸을 쓰므로 code point로 세어야 사용자가 입력한 글자 수와 같아진다.
     * {@link Post#MAX_TITLE_LENGTH}를 공유해 도메인 불변식과 판정 기준을 일치시킨다.
     *
     * <p>공백만 있는 제목은 도메인이 제목 없음으로 정규화하므로 길이를 재지 않는다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "제목은 10자 이하여야 합니다.")
    public boolean isTitleLengthValid() {
        if (title == null || title.isBlank()) {
            return true;
        }
        return title.codePointCount(0, title.length()) <= Post.MAX_TITLE_LENGTH;
    }
}
