package com.chalkak.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.api.support.ProcessingCallbackAuthenticator;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.auth.infrastructure.infra.signup.JwtSocialSignupTokenProvider;
import com.chalkak.backend.auth.service.SocialLoginResult;
import com.chalkak.backend.auth.service.SocialLoginService;
import com.chalkak.backend.like.service.PostLikeResult;
import com.chalkak.backend.like.service.PostLikeService;
import com.chalkak.backend.post.service.PostCalendarResult;
import com.chalkak.backend.post.service.PostListResult;
import com.chalkak.backend.post.service.PostQueryService;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * 필터 체인이 실제로 무엇을 막고 무엇을 여는지 고정한다. 여기서 열려 있어야 할 경로가 막히면
 * Lambda 콜백이 유실되거나 헬스체크가 실패해 배포가 멈춘다.
 *
 * <p>서비스는 모두 모킹한다. 비즈니스 계층이 던지는 401과 필터가 막아 낸 401을 구별하지 못하면
 * 통과 여부를 잘못 읽는다.
 */
@AutoConfigureMockMvc
class SecurityFilterChainTest extends IntegrationTestSupport {

    private static final String LIKE_PATH = "/api/v1/posts/{postId}/likes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @Autowired
    private JwtSocialSignupTokenProvider socialSignupTokenProvider;

    @MockitoBean
    private PostLikeService postLikeService;

    @MockitoBean
    private SocialLoginService socialLoginService;

    @MockitoBean
    private PostQueryService postQueryService;

    @MockitoBean
    private ProcessingCallbackAuthenticator processingCallbackAuthenticator;

    @Test
    @DisplayName("토큰 없이 보호된 API를 호출하면 401과 공통 에러 형식을 반환한다")
    void protectedApi_withoutToken_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(put(LIKE_PATH, UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));
    }

    @Test
    @DisplayName("유효한 액세스 토큰이 있으면 보호된 API를 호출할 수 있다")
    void protectedApi_validAccessToken_reachesController() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        given(postLikeService.likePost(postId, userId))
                .willReturn(new PostLikeResult(postId, 1L, true));
        String token = accessTokenProvider.issue(userId).value();

        // When & Then
        mockMvc.perform(put(LIKE_PATH, postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(postId.toString()));
    }

    @Test
    @DisplayName("서명이 변조된 토큰은 401을 반환한다")
    void protectedApi_tamperedToken_returnsUnauthorized() throws Exception {
        // Given
        String token = accessTokenProvider.issue(UUID.randomUUID()).value();
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        String tamperedToken = token.substring(0, signatureStart)
                + (original == 'A' ? 'B' : 'A')
                + token.substring(signatureStart + 1);

        // When & Then
        mockMvc.perform(put(LIKE_PATH, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회원가입 토큰은 액세스 토큰으로 사용할 수 없다")
    void protectedApi_socialSignupToken_returnsUnauthorized() throws Exception {
        // Given
        String signupToken = socialSignupTokenProvider.issue(
                        new VerifiedSocialIdentity(
                                SocialProvider.GOOGLE,
                                "google-subject",
                                "user@chalkak.test"),
                        UUID.randomUUID())
                .value();

        // When & Then
        mockMvc.perform(put(LIKE_PATH, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signupToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인 API는 토큰 없이 호출할 수 있다")
    void authApi_withoutToken_reachesController() throws Exception {
        // Given
        given(socialLoginService.login(any(), any()))
                .willReturn(SocialLoginResult.signUpRequired());

        // When & Then
        mockMvc.perform(post("/api/v1/auth/social-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "idToken": "google-id-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGN_UP_REQUIRED"));
    }

    @Test
    @DisplayName("주제 목록은 토큰 없이 조회할 수 있다")
    void topics_withoutToken_reachesController() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/topics"))
                .andExpect(notBlockedBySecurity());
    }

    @Test
    @DisplayName("게시물 목록은 토큰 없이 조회할 수 있다")
    void posts_withoutToken_reachesController() throws Exception {
        // Given
        given(postQueryService.getPosts(any(), any(), any(), anyInt(), anyInt(), any()))
                .willReturn(new PostListResult(0, 10, false, null, List.of()));

        // When & Then
        mockMvc.perform(get("/api/v1/posts").queryParam("topicDate", "2026-08-12"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("유효한 토큰이면 내 게시물 캘린더를 조회할 수 있다")
    void postCalendar_validAccessToken_reachesController() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(postQueryService.getMyPostCalendar(eq(userId), any()))
                .willReturn(new PostCalendarResult(2026, 8, List.of()));
        String token = accessTokenProvider.issue(userId).value();

        // When & Then
        mockMvc.perform(get("/api/v1/posts/calendar")
                        .queryParam("year", "2026")
                        .queryParam("month", "8")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(notBlockedBySecurity());
    }

    /**
     * 목록만 공개다. 상세와 캘린더는 인증 없이 열리면 안 된다.
     *
     * <p>지금은 컨트롤러도 {@code requireUserId}로 같은 판단을 하므로, 경로 규칙만 넓혀서는 이
     * 테스트가 깨지지 않는다. 컨트롤러의 검사까지 함께 사라졌을 때 열리는 것을 막는 것이 목적이다.
     */
    @Test
    @DisplayName("게시물 상세 조회는 토큰 없이 호출할 수 없다")
    void postDetail_withoutToken_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 게시물 캘린더는 토큰 없이 호출할 수 없다")
    void postCalendar_withoutToken_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/calendar")
                        .queryParam("year", "2026")
                        .queryParam("month", "8"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Lambda 콜백은 Bearer 토큰이 아니라 HMAC 헤더로 인증한다. 필터가 여기를 막으면 이미지 처리
     * 결과가 영원히 반영되지 않는다.
     */
    @Test
    @DisplayName("이미지 처리 내부 콜백은 액세스 토큰 없이 컨트롤러에 도달한다")
    void internalCallback_withoutAccessToken_reachesController() throws Exception {
        // When & Then
        mockMvc.perform(post("/internal/v1/signature-processing/{uploadId}/failed",
                        UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(notBlockedBySecurity());
    }

    /**
     * 공개 경로에서 처리되지 않은 예외가 나면 장애가 그대로 드러나야 한다. 여기서 401이 나가면
     * 클라이언트가 서버 장애를 인증 만료로 오인해 재로그인을 반복하고, 지표에서도 장애가 숨는다.
     */
    @Test
    @DisplayName("공개 경로에서 예외가 나면 익명 요청도 500을 받는다")
    void publicPath_unhandledException_returnsInternalServerError() throws Exception {
        // Given
        given(postQueryService.getPosts(any(), any(), any(), anyInt(), anyInt(), any()))
                .willThrow(new IllegalStateException("예상하지 못한 장애"));

        // When & Then
        mockMvc.perform(get("/api/v1/posts").queryParam("topicDate", "2026-08-12"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("헬스체크는 토큰 없이 호출할 수 있다")
    void health_withoutToken_returnsOk() throws Exception {
        // When & Then
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    /**
     * 공개 경로에서 확인할 것은 필터가 요청을 통과시켰는지다. 그 뒤에 어떤 상태 코드가 나오는지는
     * 데이터에 따라 달라지며 각 도메인 테스트가 따로 검증한다.
     */
    private ResultMatcher notBlockedBySecurity() {
        return result -> assertThat(result.getResponse().getStatus())
                .isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }
}
