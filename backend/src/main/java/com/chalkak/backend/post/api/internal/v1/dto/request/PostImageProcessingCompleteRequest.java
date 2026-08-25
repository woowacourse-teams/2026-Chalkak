package com.chalkak.backend.post.api.internal.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 이미지 처리 Lambda가 추출한 EXIF. 위치와 촬영 시각, 기종 정보는 S3 객체에서 제거되고 이 콜백으로만 전달되므로
 * 값이 없으면 그대로 비워 둔다.
 */
public record PostImageProcessingCompleteRequest(
        @Schema(description = "처리 후 원본 가로 픽셀", example = "4032")
        Integer width,

        @Schema(description = "처리 후 원본 세로 픽셀", example = "3024")
        Integer height,

        @Schema(description = "처리 후 원본 바이트 크기", example = "812345")
        Long byteSize,

        @Schema(description = "촬영 위치. EXIF에 GPS가 없으면 null")
        Location location,

        @Schema(
                description = "촬영 시각. EXIF에 없으면 null이고, offset이 없으면 offset 없이 전달된다",
                example = "2026-08-20T11:02:31+09:00"
        )
        String capturedAt,

        @Schema(description = "기종 등 나머지 EXIF 태그. 없으면 빈 객체")
        Map<String, Object> metaAttributes
) {

    public record Location(Double latitude, Double longitude) {
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
