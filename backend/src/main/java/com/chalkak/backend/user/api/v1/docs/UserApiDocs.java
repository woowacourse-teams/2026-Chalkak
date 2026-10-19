package com.chalkak.backend.user.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.post.api.v1.dto.request.AuthorPostListRequest;
import com.chalkak.backend.post.api.v1.dto.response.AuthorPostListResponse;
import com.chalkak.backend.user.api.v1.dto.request.UserSignatureUpdateRequest;
import com.chalkak.backend.user.api.v1.dto.response.UserSignatureDetailResponse;
import com.chalkak.backend.user.api.v1.dto.response.UserSignatureResponse;
import com.chalkak.backend.user.api.v1.dto.response.UserSignatureUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "Users", description = "사용자 API")
@SecurityRequirement(name = "accessToken")
public interface UserApiDocs {

    @Operation(summary = "작성자의 최근 공개 사진 조회", description = """
            작성자의 공개 사진을 주제 날짜 최신순으로 반환합니다. 기본 20개이며 1~100개를 지정할 수 있습니다.
            다음 조회에는 응답의 nextCursor를 cursor로 전달합니다. 마지막 묶음은 hasNext=false, nextCursor=null입니다.
            이미 지나온 구간의 새 승인은 새로고침 때 반영하며, 미조회 구간은 요청 시점의 공개 조건을 따릅니다.
            정지 회원도 조회할 수 있고 공개 사진이 없으면 빈 목록을 반환합니다.
            본인 조회에도 승인 대기·반려 사진은 포함하지 않습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "최근 공개 사진 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 작성자 ID 형식 또는 조회 조건", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "일반 사용자 API에 접근할 수 없는 토큰", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "작성자를 찾을 수 없음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AuthorPostListResponse> getAuthorPosts(
            @Parameter(name = "userId", description = "사진 작성자의 회원 ID", schema = @Schema(type = "string", format = "uuid")) String authorId,
            @ParameterObject AuthorPostListRequest request,
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "회원 탈퇴", description = "회원 탈퇴와 함께 모든 로그인 RT를 폐기하고 "
            + "연결된 푸시 기기를 비활성화하며 FCM 토큰을 제거합니다. 별도 기기 해제 요청은 필요하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "회원 탈퇴 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> withdraw(
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "사인 이미지 업로드 URL 발급")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 URL 발급 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "이용이 정지된 회원", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserSignatureUploadResponse> createSignatureUpload(
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "내 사인 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사인 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "사인 재등록 필요(SIGNATURE_REGISTRATION_REQUIRED): "
                    + "사인 이미지 처리 실패 또는 설정된 처리 제한 시간 초과", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserSignatureDetailResponse> getSignature(
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "사인 이미지 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사인 이미지 수정 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "잘못된 이미지 업로드 정보 또는 이미지 재업로드 필요"
                    + "(SIGNATURE_REUPLOAD_REQUIRED)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "이용이 정지된 회원", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "업로드 이미지를 찾을 수 없음", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserSignatureResponse> updateSignature(
            @Parameter(hidden = true) AuthenticatedUser loginUser,
            UserSignatureUpdateRequest request
    );
}
