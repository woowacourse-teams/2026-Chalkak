package com.chalkak.backend.filter.api.v1.dto.request;

import com.chalkak.backend.common.util.CanonicalUuidParser;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record FilterDraftUpdateRequest(
        @Schema(description = "필터 이름. 최대 30자이며 공백만 보내면 지웁니다. 생략 또는 null이면 유지합니다.", nullable = true)
        String name,
        @Schema(description = "필터 설명. 최대 500자이며 공백만 보내면 지웁니다. 생략 또는 null이면 유지합니다.", nullable = true)
        String description,
        @Schema(description = "소개 사진 업로드 ID 목록. 요청 순서대로 교체하며 생략 또는 null이면 유지합니다.", nullable = true)
        @Size(min = 1, max = 5, message = "소개 사진은 1~5장까지 연결할 수 있습니다.")
        List<@NotBlank(message = "사진 업로드 ID는 비워 둘 수 없습니다.") String> uploadIds
) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "수정할 필터 정보를 하나 이상 입력해주세요.")
    public boolean isUpdatePresent() {
        return name != null || description != null || uploadIds != null;
    }

    public List<UUID> parseUploadIds() {
        if (uploadIds == null) {
            return null;
        }
        return uploadIds.stream().map(CanonicalUuidParser::parse).toList();
    }
}
