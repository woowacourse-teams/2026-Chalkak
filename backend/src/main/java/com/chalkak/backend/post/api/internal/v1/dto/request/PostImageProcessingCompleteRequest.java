package com.chalkak.backend.post.api.internal.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 이미지 처리 Lambda가 추출한 EXIF. 위치와 촬영 시각, 기종 정보는 S3 객체에서 제거되고 이 콜백으로만 전달되므로
 * 값이 없으면 그대로 비워 둔다.
 *
 * <p>값의 상당 부분이 공격자가 만든 파일의 EXIF에서 온다. 서명 검증 때문에 본문을 원문으로 받아
 * {@code @Valid}가 걸리지 않으므로 컨트롤러가 이 제약을 직접 검사한다.
 */
public record PostImageProcessingCompleteRequest(
        @Positive
        @Schema(description = "처리 후 원본 가로 픽셀", example = "4032")
        Integer width,

        @Positive
        @Schema(description = "처리 후 원본 세로 픽셀", example = "3024")
        Integer height,

        @PositiveOrZero
        @Schema(description = "처리 후 원본 바이트 크기", example = "812345")
        Long byteSize,

        @Valid
        @Schema(description = "촬영 위치. EXIF에 GPS가 없으면 null")
        Location location,

        @Schema(
                description = "촬영 시각. EXIF에 없으면 null이고, offset이 없으면 offset 없이 전달된다",
                example = "2026-08-20T11:02:31+09:00"
        )
        @Size(max = MAX_CAPTURED_AT_LENGTH)
        String capturedAt,

        @Size(max = MAX_META_ATTRIBUTES)
        @Schema(description = "기종 등 나머지 EXIF 태그. 없으면 빈 객체")
        Map<String, Object> metaAttributes
) {

    public static final int MAX_CAPTURED_AT_LENGTH = 64;
    public static final int MAX_META_ATTRIBUTES = 200;

    public record Location(
            @NotNull
            @DecimalMin("-90.0")
            @DecimalMax("90.0")
            Double latitude,

            @NotNull
            @DecimalMin("-180.0")
            @DecimalMax("180.0")
            Double longitude
    ) {
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("width", width);
        metadata.put("height", height);
        metadata.put("byteSize", byteSize);
        metadata.put("location", location);
        metadata.put("capturedAt", capturedAt);
        metadata.put("metaAttributes", (metaAttributes == null) ? Map.of() : metaAttributes);

        return metadata;
    }
}
