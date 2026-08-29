package com.chalkak.backend.auth.api.support;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정지 회원이 무엇을 할 수 있고 무엇을 할 수 없는지 통째로 고정한다.
 *
 * <p>정지는 남에게 보이는 것을 새로 만들거나 바꾸는 행위만 막는다. 조회와 자기 데이터 정리는 정지
 * 중에도 할 수 있어야 한다. {@link RequiresUsableUser}를 잘못 붙이거나 빠뜨리면 여기서 드러난다.
 */
@Transactional
@AutoConfigureMockMvc
class SuspendedUserAccessTest extends IntegrationTestSupport {

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
     * 사인 조회가 스토리지 키를 URL로 바꾸므로 root-prefix에 맞는 키를 넣는다. 픽스처의 기본 키를
     * 쓰면 인가와 무관한 이유로 요청이 실패해 이 테스트가 무엇을 검증하는지 흐려진다.
     */
    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status,
                    signature_original_storage_key, signature_thumbnail_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'suspended@chalkak.test', 'BANNED',
                    'chalkak/signatures/original/suspended.png',
                    'chalkak/signatures/thumbnail/suspended.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, userId);
        token = "Bearer " + accessTokenProvider.issue(userId).value();
    }

    @Test
    @DisplayName("정지 회원은 게시물 사진 업로드 URL을 발급받을 수 없다")
    void createPostImageUpload_suspendedUser_returnsForbidden() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/posts/uploads")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(suspended());
    }

    @Test
    @DisplayName("정지 회원은 게시물을 생성할 수 없다")
    void createPost_suspendedUser_returnsForbidden() throws Exception {
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
                .andExpect(suspended());
    }

    @Test
    @DisplayName("정지 회원은 좋아요를 등록할 수 없다")
    void likePost_suspendedUser_returnsForbidden() throws Exception {
        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}/likes", POST_ID)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(suspended());
    }

    @Test
    @DisplayName("정지 회원은 사인 업로드 URL을 발급받을 수 없다")
    void createSignatureUpload_suspendedUser_returnsForbidden() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/users/me/signature/uploads")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(suspended());
    }

    @Test
    @DisplayName("정지 회원은 사인을 교체할 수 없다")
    void updateSignature_suspendedUser_returnsForbidden() throws Exception {
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
                .andExpect(suspended());
    }

    @Test
    @DisplayName("정지 회원도 좋아요를 취소할 수 있다")
    void unlikePost_suspendedUser_isNotForbidden() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", POST_ID)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(notSuspended());
    }

    @Test
    @DisplayName("정지 회원도 탈퇴할 수 있다")
    void withdraw_suspendedUser_isNotForbidden() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(notSuspended());
    }

    @Test
    @DisplayName("정지 회원도 내 게시물 캘린더를 조회할 수 있다")
    void getMyPostCalendar_suspendedUser_isNotForbidden() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/calendar")
                        .queryParam("year", "2026")
                        .queryParam("month", "8")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(notSuspended());
    }

    @Test
    @DisplayName("정지 회원도 내 사인을 조회할 수 있다")
    void getSignature_suspendedUser_isNotForbidden() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/users/me/signature")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(notSuspended());
    }

    @Test
    @DisplayName("정지 회원도 게시물 목록을 조회할 수 있다")
    void getPosts_suspendedUser_isNotForbidden() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("topicDate", "2026-08-12")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(notSuspended());
    }

    @Test
    @DisplayName("정지 회원도 게시물 상세를 조회할 수 있다")
    void getPost_suspendedUser_isNotForbidden() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", POST_ID)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(notSuspended());
    }

    private ResultMatcher suspended() {
        return result -> {
            status().isForbidden().match(result);
            jsonPath("$.errorCode").value("FORBIDDEN").match(result);
            jsonPath("$.message").value("차단된 회원입니다.").match(result);
        };
    }

    /**
     * 허용된 요청은 데이터가 없어 404 등으로 끝날 수 있다. 여기서 볼 것은 정지를 이유로 막히지
     * 않았다는 사실뿐이다.
     */
    private ResultMatcher notSuspended() {
        return result -> assertThat(result.getResponse().getStatus())
                .isNotEqualTo(HttpStatus.FORBIDDEN.value());
    }
}
