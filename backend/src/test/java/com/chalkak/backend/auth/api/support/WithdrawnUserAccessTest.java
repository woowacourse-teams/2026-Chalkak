package com.chalkak.backend.auth.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 회원이 남은 토큰으로 무엇도 할 수 없다는 사실을 통째로 고정한다.
 *
 * <p>탈퇴는 상태가 아니라 부재로 다룬다. 토큰은 탈퇴해도 만료 전까지 살아 있으므로, 인증 자체는
 * 통과하고 회원이 없다는 사실만 남는다. 그 사실에 대한 답은 화면마다 달라지면 안 되고 언제나
 * 401 {@code UNAUTHORIZED}여야 한다. {@link RequiresUsableUser}와 {@link RequiresExistingUser}
 * 중 무엇을 붙였든 결과가 같아야 하므로 두 계열을 한 클래스에서 함께 본다.
 */
@Transactional
@AutoConfigureMockMvc
class WithdrawnUserAccessTest extends IntegrationTestSupport {

    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    private String token;

    /**
     * {@code deleted_at}이 찍힌 회원에게 아직 유효한 토큰을 쥐여 준다. 탈퇴 직후 남은 토큰으로
     * 다시 들어오는 상황이 이 테스트가 재현하려는 유일한 상황이다.
     */
    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status,
                    signature_original_storage_key, signature_thumbnail_storage_key,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, 'withdrawn@chalkak.test', 'ACTIVE',
                    'chalkak/signatures/original/withdrawn.png',
                    'chalkak/signatures/thumbnail/withdrawn.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, userId);
        token = "Bearer " + accessTokenProvider.issue(userId).value();
    }

    @Test
    @DisplayName("탈퇴 회원은 게시물 사진 업로드 URL을 발급받을 수 없다")
    void createPostImageUpload_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts/uploads")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(withdrawn());
    }

    @Test
    @DisplayName("탈퇴 회원은 게시물을 생성할 수 없다")
    void createPost_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topicId": "0198f6c1-62ba-7d30-8b12-0f733b6570b2",
                                  "photoUploadId": "0198f6c1-62ba-7d30-8b12-0f733b6570d4",
                                  "title": "제목"
                                }
                                """))
                .andExpect(withdrawn());
    }

    @Test
    @DisplayName("탈퇴 회원은 좋아요를 등록할 수 없다")
    void likePost_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}/likes", POST_ID)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(withdrawn());
    }

    @Test
    @DisplayName("탈퇴 회원은 사인 업로드 URL을 발급받을 수 없다")
    void createSignatureUpload_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/users/me/signature/uploads")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(withdrawn());
    }

    @Test
    @DisplayName("탈퇴 회원은 사인을 교체할 수 없다")
    void updateSignature_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/users/me/signature")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "signatureOriginalUploadId":
                                      "0198f6c1-62ba-7d30-8b12-0f733b6570e1"
                                }
                                """))
                .andExpect(withdrawn());
    }

    @Test
    @DisplayName("탈퇴 회원은 다시 탈퇴할 수 없다")
    void withdraw_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(withdrawn());
    }

    @Test
    @DisplayName("탈퇴 회원은 내 사인을 조회할 수 없다")
    void getSignature_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users/me/signature")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(withdrawn());
    }

    @Test
    @DisplayName("탈퇴 회원은 게시물을 삭제할 수 없다")
    void deletePost_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/posts/{postId}", POST_ID)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(withdrawn());
    }

    @Test
    @DisplayName("탈퇴 회원은 내 게시물 캘린더를 조회할 수 없다")
    void getMyPostCalendar_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/calendar")
                        .queryParam("year", "2026")
                        .queryParam("month", "8")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(withdrawn());
    }

    @Test
    @DisplayName("탈퇴 회원은 게시물 상세를 조회할 수 없다")
    void getPost_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", POST_ID)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(withdrawn());
    }

    @Test
    @DisplayName("탈퇴 회원은 좋아요를 취소할 수 없다")
    void unlikePost_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", POST_ID)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(withdrawn());
    }

    /**
     * 게시물 목록만 회원 상태 판정 표시가 없다. 비로그인 조회를 허용해야 해서 표시를 붙일 수 없고,
     * 대신 {@code PostQueryService}가 식별자가 있을 때만 회원을 확인한다. 표시가 없는 경로라
     * 다른 답을 내놓기 쉬운 자리이므로, 여기서도 결과가 401로 같다는 사실을 따로 못박아 둔다.
     */
    @Test
    @DisplayName("탈퇴 회원은 게시물 목록도 조회할 수 없다")
    void getPosts_withdrawnUser_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("topicDate", "2026-08-12")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(withdrawn());
    }

    private ResultMatcher withdrawn() {
        return result -> {
            status().isUnauthorized().match(result);
            jsonPath("$.errorCode").value("UNAUTHORIZED").match(result);
        };
    }
}
