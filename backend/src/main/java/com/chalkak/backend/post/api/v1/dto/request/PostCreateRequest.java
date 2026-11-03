package com.chalkak.backend.post.api.v1.dto.request;

import com.chalkak.backend.post.domain.PostDescription;
import com.chalkak.backend.post.domain.PostInstagramConsent;
import com.chalkak.backend.post.domain.PostTitle;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PostCreateRequest(
        @Schema(description = "주제 ID", example = "0198f6c1-62ba-7d30-8b12-0f733b6570b2")
        @NotNull(message = "주제 정보가 올바르지 않습니다.")
        UUID topicId,

        @Schema(description = "사진 업로드 ID", example = "0198f6c1-62ba-7d30-8b12-0f733b6570d4")
        @NotNull(message = "사진 업로드 정보가 올바르지 않습니다.")
        UUID photoUploadId,

        @Schema(
                description = "작품 제목. 앞뒤 공백 제거 후 최대 10자입니다. 생략하거나 공백뿐이면 제목 없음으로 저장합니다.",
                example = "오늘의 기록",
                nullable = true)
        String title,

        @Schema(
                description = "작품 설명. 앞뒤 공백과 줄바꿈 제거 후 최대 300자입니다. 가운데 줄바꿈은 유지하며 글자 수에 포함합니다. 생략하거나 공백뿐이면 설명 없음으로 저장합니다.",
                example = "퇴근길에 만난 노을을 담았어요.",
                nullable = true)
        String description,

        @Schema(
                description = "공식 인스타그램 게시 동의 여부. 생략하거나 null이면 동의하지 않은 것으로 처리합니다.",
                example = "true",
                defaultValue = "false",
                nullable = true)
        Boolean instagramUploadAgreed,

        @Schema(
                description = "태그에 사용할 인스타그램 ID. 동의한 경우에만 보낼 수 있습니다. 앞뒤 공백과 맨 앞 @ 하나를 제거하고 소문자로 저장하며, 이후 영문 소문자·숫자·마침표·밑줄로 1~30자여야 합니다. 생략하거나 비어 있으면 ID 없음으로 저장합니다.",
                example = "chalkak.official",
                nullable = true)
        String instagramId
) {

    /**
     * 필드 단위 400 응답을 위한 빠른 실패. 판정 기준은 {@link PostTitle}에 위임해 도메인 불변식과 어긋나지 않게 한다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "제목은 10자 이하여야 합니다.")
    public boolean isTitleLengthValid() {
        return PostTitle.isLengthValid(title);
    }

    /**
     * 필드 단위 400 응답을 위한 빠른 실패. 판정 기준은 {@link PostDescription}에 위임해 도메인 불변식과 어긋나지 않게
     * 한다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "설명은 300자 이하여야 합니다.")
    public boolean isDescriptionLengthValid() {
        return PostDescription.isLengthValid(description);
    }

    /**
     * 필드 단위 400 응답을 위한 빠른 실패. 판정 기준은 {@link PostTitle}에 위임해 도메인 불변식과 어긋나지 않게 한다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "제목에 사용할 수 없는 문자가 포함되어 있습니다.")
    public boolean isTitleCharacterValid() {
        return PostTitle.isCharacterValid(title);
    }

    /**
     * 필드 단위 400 응답을 위한 빠른 실패. 판정 기준은 {@link PostDescription}에 위임해 도메인 불변식과 어긋나지 않게
     * 한다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "설명에 사용할 수 없는 문자가 포함되어 있습니다.")
    public boolean isDescriptionCharacterValid() {
        return PostDescription.isCharacterValid(description);
    }

    /**
     * 필드 단위 400 응답을 위한 빠른 실패. 판정 기준은 {@link PostInstagramConsent}에 위임해 도메인 불변식과
     * 어긋나지 않게 한다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = PostInstagramConsent.INVALID_ID_FORMAT_MESSAGE)
    public boolean isInstagramIdFormatValid() {
        return PostInstagramConsent.isIdFormatValid(instagramId);
    }

    /**
     * 필드 단위 400 응답을 위한 빠른 실패. 판정 기준은 {@link PostInstagramConsent}에 위임해 도메인 불변식과
     * 어긋나지 않게 한다.
     */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = PostInstagramConsent.ID_WITHOUT_AGREEMENT_MESSAGE)
    public boolean isInstagramIdAllowedWithAgreement() {
        return PostInstagramConsent.isIdAllowedWithAgreement(
                Boolean.TRUE.equals(instagramUploadAgreed),
                instagramId
        );
    }
}
