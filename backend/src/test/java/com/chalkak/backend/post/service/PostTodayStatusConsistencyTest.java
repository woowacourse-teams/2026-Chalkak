package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사전 확인과 게시물 생성이 같은 답을 내는지 검증한다.
 *
 * <p>사전 확인이 통과시킨 요청이 생성에서 거절되거나 그 반대가 되면 클라이언트가 잘못된 안내를 한다. 두
 * 흐름은 조건이 같은 별도의 쿼리를 쓰므로, 한쪽만 바뀌어도 컴파일과 개별 테스트는 통과한다. 그래서 각 규칙을
 * 따로 검증하는 대신 두 흐름의 답이 서로 같은지를 단언한다.
 */
@Transactional
class PostTodayStatusConsistencyTest extends IntegrationTestSupport {

    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d2");
    private static final UUID PHOTO_UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d3");
    private static final UUID EXISTING_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID EXISTING_PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d5");
    private static final String ORIGINAL_STORAGE_KEY =
            "chalkak/posts/test/original/" + PHOTO_UPLOAD_ID + ".webp";
    private static final String DUPLICATE_MESSAGE = "이미 해당 주제에 게시물을 작성했습니다.";

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private PostQueryService postQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @MockitoBean
    private RandomSeedGenerator randomSeedGenerator;

    @MockitoBean
    private PostImageStorage postImageStorage;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'today-consistency@example.com', 'ACTIVE',
                    'chalkak/signatures/test/original/consistency.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '오늘 작성 여부 일치 검증 주제', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
        jdbcTemplate.update("""
                INSERT INTO post_image_uploads (
                    id, user_id, status, expires_at, created_at, updated_at
                ) VALUES (
                    ?, ?, CAST('ISSUED' AS post_image_upload_status),
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PHOTO_UPLOAD_ID, USER_ID);
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "이미지 처리 대기 중,       VALIDATING, 1 minute,  false",
            "이미지 처리 시간 초과,     VALIDATING, 8 minutes, false",
            "검수 대기,                PENDING,    1 minute,  false",
            "검수 승인,                APPROVED,   1 minute,  false",
            "검수 거절,                REJECTED,   1 minute,  false",
            "작성자가 삭제,            APPROVED,   1 minute,  true"
    })
    @DisplayName("기존 게시물이 있을 때 사전 확인과 게시물 생성이 같은 답을 낸다")
    void getMyTodayPostStatus_existingPost_agreesWithCreatePost(
            String scenario,
            ModerationStatus moderationStatus,
            String createdBefore,
            boolean deleted
    ) {
        // Given
        insertExistingPost(moderationStatus, createdBefore, deleted);

        // When
        boolean isPosted = postQueryService.getMyTodayPostStatus(USER_ID).isPosted();
        boolean createRejected = isCreateRejectedAsDuplicate();

        // Then
        assertThat(isPosted)
                .as("%s: 사전 확인과 게시물 생성의 판정이 달라졌다", scenario)
                .isEqualTo(createRejected);
    }

    @Test
    @DisplayName("기존 게시물이 없을 때 사전 확인과 게시물 생성이 같은 답을 낸다")
    void getMyTodayPostStatus_noExistingPost_agreesWithCreatePost() {
        // When
        boolean isPosted = postQueryService.getMyTodayPostStatus(USER_ID).isPosted();
        boolean createRejected = isCreateRejectedAsDuplicate();

        // Then
        assertThat(isPosted).isEqualTo(createRejected);
        assertThat(isPosted).isFalse();
    }

    /**
     * 중복 이외의 이유로 생성이 실패하면 그대로 던진다. 실패를 중복으로 뭉뚱그리면 두 흐름이 어긋나도
     * 테스트가 통과해 버린다.
     */
    private boolean isCreateRejectedAsDuplicate() {
        try {
            postCommandService.createPost(USER_ID, TOPIC_ID, PHOTO_UPLOAD_ID, "제목");
            return false;
        } catch (BusinessException exception) {
            if (DUPLICATE_MESSAGE.equals(exception.getMessage())) {
                return true;
            }
            throw exception;
        }
    }

    private void insertExistingPost(
            ModerationStatus moderationStatus,
            String createdBefore,
            boolean deleted
    ) {
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'chalkak/posts/test/original/existing.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, EXISTING_PHOTO_ID);
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, moderation_status,
                    deleted_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?::moderation_status,
                    CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,
                    CURRENT_TIMESTAMP - CAST(? AS INTERVAL), CURRENT_TIMESTAMP
                )
                """,
                EXISTING_POST_ID,
                USER_ID,
                TOPIC_ID,
                EXISTING_PHOTO_ID,
                moderationStatus.name(),
                deleted,
                createdBefore
        );
        entityManager.flush();
        entityManager.clear();
    }
}
