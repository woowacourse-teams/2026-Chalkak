package com.chalkak.backend.user.api.v1.docs;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.exception.ErrorResponse;
import com.chalkak.backend.post.api.v1.dto.request.AuthorPostListRequest;
import com.chalkak.backend.post.api.v1.dto.response.AuthorPostListResponse;
import com.chalkak.backend.user.api.v1.dto.request.ArtistApplicationRequest;
import com.chalkak.backend.user.api.v1.dto.request.UserSignatureUpdateRequest;
import com.chalkak.backend.user.api.v1.dto.response.ArtistEligibilityResponse;
import com.chalkak.backend.user.api.v1.dto.response.UserProfileResponse;
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

    @Operation(summary = "내 회원 역할과 작가 동의 여부 조회", description = "정지 회원도 조회할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원 정보 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "일반 사용자 API에 접근할 수 없는 토큰", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserProfileResponse> getMyProfile(
            @Parameter(hidden = true) AuthenticatedUser loginUser);

    @Operation(summary = "작가 신청 자격 조회", description = """
            현재 공개 중인 본인 게시물의 좋아요 합계와 신청에 필요한 수를 반환합니다.
            정지 회원과 이미 작가인 회원도 조회할 수 있으며 신청 가능 여부는 false입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작가 신청 자격 조회 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "일반 사용자 API에 접근할 수 없는 토큰", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ArtistEligibilityResponse> getArtistEligibility(
            @Parameter(hidden = true) AuthenticatedUser loginUser
    );

    @Operation(summary = "작가 신청과 원작 확약 동의", description = """
            원작 확약 동의가 필요합니다. 일반 회원은 신청 시점의 공개 게시물 좋아요 기준을 충족하면 작가가 됩니다.
            이미 작가인 회원은 최초 동의만 기록하며, 같은 요청을 다시 보내면 기존 동의 시각을 유지합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작가 신청 또는 동의 완료", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "원작 확약 동의 누락·거부 또는 좋아요 기준 미충족", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "정지 회원 또는 일반 사용자 API에 접근할 수 없는 토큰", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserProfileResponse> applyForArtist(
            @Parameter(hidden = true) AuthenticatedUser loginUser,
            ArtistApplicationRequest request
    );

    @Operation(summary = "작성자의 최근 공개 사진 조회", description = """
            작성자의 공개 사진을 주제 날짜 최신순으로 반환합니다. 기본 20개이며 1~100개를 지정할 수 있습니다.
            page는 1부터 시작하며 생략하면 1페이지를 조회합니다. 응답의 hasNext가 true이면 다음 페이지를 조회합니다.
            각 요청 시점의 공개 사진을 기준으로 하므로 조회 중 승인·삭제에 따라 중복되거나 건너뛰는 사진이 생길 수 있습니다.
            정지 회원도 조회할 수 있고 공개 사진이 없으면 빈 목록을 반환합니다.
            본인 조회에도 승인 대기·반려 사진은 포함하지 않습니다.
            각 사진에 현재 전체 좋아요 수와 인증된 조회자 본인의 좋아요 여부를 제공합니다. 비로그인 조회는 허용하지 않습니다.
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
