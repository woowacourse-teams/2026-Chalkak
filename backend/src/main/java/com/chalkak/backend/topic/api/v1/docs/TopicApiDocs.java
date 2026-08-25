package com.chalkak.backend.topic.api.v1.docs;

import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.topic.api.v1.dto.response.TopicDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Topics", description = "주제 API")
public interface TopicApiDocs {

    @Operation(summary = "주제 조회")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "주제 조회 성공",
                    useReturnTypeSchema = true
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 날짜 형식",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "주제를 찾을 수 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<TopicDetailResponse> getTopic(
            @Parameter(description = "조회할 주제 날짜", example = "2026-08-12")
            LocalDate date
    );
}
