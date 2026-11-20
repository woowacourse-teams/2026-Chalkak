package com.chalkak.backend.jev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.jev.domain.JevCallResult;
import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevPhase;
import com.chalkak.backend.jev.domain.JevPromptDraft;
import com.chalkak.backend.jev.domain.JevPromptFixtures;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class JevJobServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN = UUID.fromString("019ad963-213b-734e-a261-60e568c13056");
    private static final UUID USER = UUID.fromString("019ad963-213b-734e-a261-60e568c13057");
    private static final UUID TOPIC = UUID.fromString("019ad963-213b-734e-a261-60e568c13058");
    private static final UUID POST = UUID.fromString("019ad963-213b-734e-a261-60e568c13059");
    private static final UUID PHOTO = UUID.fromString("019ad963-213b-734e-a261-60e568c13060");
    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

    @Autowired
    private JevJobService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private Clock clock;
    @Value("${IMAGE_PROCESSOR_CALLBACK_SECRET}")
    private String secret;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        jdbc.update("DELETE FROM admins WHERE id = ?", ADMIN);
        given(clock.instant()).willReturn(NOW);
        jdbc.update("INSERT INTO admins(id, username, password) VALUES (?, 'jev-job-test', 'test')",
                ADMIN);
        jdbc.update(
                "INSERT INTO users(id, email, status, signature_original_storage_key) VALUES (?, 'jev-job@test.example', 'ACTIVE', 'chalkak/signatures/test/job.png')",
                USER);
        jdbc.update(
                """
                        INSERT INTO topics(id, title, topic_date, starts_at, ends_at)
                        VALUES (?, '초록', '2026-11-20', ?, ?)
                        """,
                TOPIC,
                Timestamp.from(NOW.minusSeconds(1)),
                Timestamp.from(NOW.plusSeconds(3600))
        );
        jdbc.update(
                "INSERT INTO photos(id, original_storage_key, thumbnail_storage_key) VALUES (?, ?, ?)",
                PHOTO,
                "chalkak/posts/test/original/" + PHOTO + ".webp",
                "chalkak/posts/test/thumbnail/" + PHOTO + ".webp"
        );
        jdbc.update(
                "INSERT INTO posts(id, user_id, topic_id, photo_id, moderation_status) VALUES (?, ?, ?, ?, 'APPROVED')",
                POST,
                USER,
                TOPIC,
                PHOTO
        );
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbc).clean();
        jdbc.update("DELETE FROM admins WHERE id = ?", ADMIN);
    }

    @Test
    @DisplayName("초안 시험은 세 질문의 반려 추천과 원응답을 보존하고 실제 게시물과 알림을 바꾸지 않는다")
    void preview_rejectRecommendation_retainsActualPost() {
        // Given
        UUID id = create();
        completeClassification(id);
        JevJobClaim claim = service.claim(id);
        assertThat(claim.action()).isEqualTo("INSPECT");
        assertThat(claim.questions()).hasSize(3);
        assertThat(claim.questions().get("topic_fit").instructions()).contains("테스트 사물 규칙");
        // When
        service.receiveResult(
                id,
                "PHOTO",
                success(claim, photoResponse(false))
        );
        // Then
        var run = service.getRun(id);
        assertThat(run.getEvaluation().recommendation()).isEqualTo(JevDecision.REJECT);
        assertThat(run.getPhase().calls()).hasSize(1);
        assertThat(run.getPhase().calls().getFirst().startedAt()).isEqualTo(NOW);
        assertThat(run.getFinishedAt()).isEqualTo(NOW);
        assertThat(service.claim(id).action()).isEqualTo("STOP");
        assertThat(jdbc.queryForObject(
                "SELECT moderation_status FROM posts WHERE id = ?",
                String.class,
                POST
        ))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ai_prompt_versions WHERE type LIKE 'JEV_%'", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("같은 분류 초안과 주제는 DB 분류 하나를 공유하고 한 번의 사용량만 보관한다")
    void preview_sameTopic_reusesOneClassification() {
        // Given
        UUID first = create();
        UUID second = create();
        JevJobClaim claim = service.claim(first);
        assertThat(service.claim(second).action()).isEqualTo("WAIT");
        // When
        service.receiveResult(
                first,
                "CLASSIFICATION",
                success(claim, classificationResponse())
        );
        // Then
        assertThat(service.claim(second).action()).isEqualTo("INSPECT");
        assertThat(service.getRun(first).getClassificationId())
                .isEqualTo(service.getRun(second).getClassificationId());
        assertThat(service.getCategory(second).getPhase().calls()).hasSize(1);
        assertThat(service.getCategory(second).getEvaluation().inputTokens()).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM jev_topic_categories", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("겹친 작업 확보 요청은 실제 DB 잠금으로 물리 분류 호출 하나만 발급한다")
    void claim_concurrentRequests_issuesOneCall() throws Exception {
        // Given
        UUID first = create();
        UUID second = create();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> {
                start.await();
                return service.claim(first).action();
            });
            var two = executor.submit(() -> {
                start.await();
                return service.claim(second).action();
            });
            // When
            start.countDown();
            // Then
            assertThat(List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CLASSIFY", "WAIT");
        }
    }

    @Test
    @DisplayName("결과 재전달은 원문과 시도 횟수를 중복하지 않고 발급하지 않은 ID는 거절한다")
    void receive_duplicateAndUnknownCall_keepsOneAttempt() {
        // Given
        UUID id = create();
        JevJobClaim claim = service.claim(id);
        JevCallResult result = success(claim, classificationResponse());
        // When
        service.receiveResult(
                id,
                "CLASSIFICATION",
                result
        );
        service.receiveResult(
                id,
                "CLASSIFICATION",
                result
        );
        // Then
        assertThat(service.getCategory(id).getPhase().calls()).hasSize(1);
        JevCallResult forged = new JevCallResult(
                UUID.randomUUID(),
                null,
                null,
                "{}",
                null,
                200,
                1L,
                null
        );
        assertThatThrownBy(() -> service.receiveResult(
                id,
                "CLASSIFICATION",
                forged
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("분류 실패는 재사용하며 새 사진마다 무한 재분류하지 않는다")
    void claim_classificationFailed_returnsManualReviewWithoutNewCall() {
        // Given
        UUID first = create();
        JevJobClaim claim = service.claim(first);
        service.receiveResult(
                first,
                "CLASSIFICATION",
                new JevCallResult(
                        claim.callId(),
                        null,
                        null,
                        null,
                        "HTTP_ERROR",
                        401,
                        12L,
                        null
                )
        );
        // When
        UUID next = create();
        // Then
        assertThat(service.claim(next).action()).isEqualTo("STOP");
        assertThat(service.getRun(next).getEvaluation().recommendation())
                .isEqualTo(JevDecision.HUMAN_REVIEW);
        assertThat(service.getCategory(next).getPhase().calls()).hasSize(1);
    }

    @Test
    @DisplayName("작업 전달 예약은 전송 중복을 줄이고 시각이 지나면 재시작 후에도 재전달한다")
    void reserveDispatch_lostDelivery_becomesAvailableAgain() {
        // Given
        UUID id = create();
        // When & Then
        assertThat(service.reserveDispatch(id)).isEqualTo(1);
        assertThat(service.reserveDispatch(id)).isZero();
        service.recordDispatch(
                id,
                1,
                "QueueUnavailable"
        );
        assertThat(service.getRun(id).getDispatchError()).isEqualTo("QueueUnavailable");
        given(clock.instant()).willReturn(NOW.plusSeconds(15));
        assertThat(service.reserveDispatch(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("삭제된 사진은 관리자 시험 대상으로 새로 등록할 수 없다")
    void createPreview_deletedSource_rejects() {
        // Given
        jdbc.update("UPDATE photos SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", PHOTO);
        // When & Then
        assertThatThrownBy(this::create).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Jev 내부 API도 서명 없는 호출을 거절하고 환경/본문 서명으로 확보한다")
    void callback_signedClaim_authenticatesRawBody() throws Exception {
        // Given
        UUID id = create();
        byte[] body = mapper.writeValueAsBytes(Map.of("jobId", id));
        String path = "/internal/v1/jev-moderation-jobs/claims";
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        // When & Then
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Chalkak-Callback-Timestamp", timestamp)
                .header("X-Chalkak-Callback-Signature", sign(
                        timestamp,
                        path,
                        body
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("CLASSIFY"))
                .andExpect(jsonPath("$.model").value("jev-1.14.0"));
    }

    @Test
    @DisplayName("서명된 결과는 204로 저장하고 서명한 본문을 바꾸면 반영하지 않는다")
    void callback_signedResult_preservesRawResponseAndRejectsTampering() throws Exception {
        // Given
        UUID id = create();
        JevJobClaim claim = service.claim(id);
        String path = "/internal/v1/jev-moderation-jobs/" + id + "/results";
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        byte[] body = mapper.writeValueAsBytes(Map.of(
                "callId",
                claim.callId(),
                "stage",
                "CLASSIFICATION",
                "rawResponse",
                classificationResponse(),
                "httpStatus",
                200,
                "latencyMs",
                123
        ));
        String signature = sign(
                timestamp,
                path,
                body
        );
        // When & Then
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("X-Chalkak-Callback-Timestamp", timestamp)
                .header("X-Chalkak-Callback-Signature", signature))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Chalkak-Callback-Timestamp", timestamp)
                .header("X-Chalkak-Callback-Signature", signature))
                .andExpect(status().isNoContent());
        assertThat(service.getCategory(id).getPhase().calls().getFirst().rawResponse())
                .isEqualTo(classificationResponse());
    }

    @Test
    @DisplayName("사진 결과의 질문 누락도 DB에 기술 검토로 남기고 모델을 다시 부르지 않는다")
    void receive_missingPhotoQuestion_finishesWithTechnicalReview() {
        // Given
        UUID id = create();
        completeClassification(id);
        JevJobClaim claim = service.claim(id);
        // When
        service.receiveResult(
                id,
                "PHOTO",
                success(claim, photoResponse(true))
        );
        // Then
        assertThat(service.getRun(id).getEvaluation().error())
                .isEqualTo("MISSING_OR_INVALID_QUESTIONS");
        assertThat(service.getRun(id).getEvaluation().recommendation())
                .isEqualTo(JevDecision.HUMAN_REVIEW);
        assertThat(service.claim(id).action()).isEqualTo("STOP");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLASSIFICATION", "PHOTO"})
    @DisplayName("오류 재시도는 단계의 2초와 4초 대기 시각에 전달하고 즉시 WAIT도 예약을 보존한다")
    void receive_retryableFailure_schedulesAtNextAvailableTime(String stage) {
        // Given
        UUID id = createAtStage(stage);
        JevJobClaim claim = service.claim(id);
        Instant deadline = claim.deadline();
        Instant startedAt = NOW;
        // When & Then
        for (int failure = 1; failure <= 2; failure++) {
            Instant receivedAt = startedAt.plusSeconds(30);
            given(clock.instant()).willReturn(receivedAt);
            service.receiveResult(
                    id,
                    stage,
                    retryable(claim, null)
            );
            JevPhase phase = getPhase(id, stage);
            Instant next = phase.availableAt();
            int backoff = failure == 1 ? 2 : 4;
            assertThat(next).isBetween(receivedAt.plusSeconds(backoff),
                    receivedAt.plusSeconds(backoff + 1));
            assertThat(service.getRun(id).getDispatchAt()).isEqualTo(next);
            assertThat(service.claim(id).action()).isEqualTo("WAIT");
            assertThat(service.getRun(id).getDispatchAt()).isEqualTo(next);
            given(clock.instant()).willReturn(next.minusMillis(1));
            assertThat(service.findDueRunIds()).doesNotContain(id);
            assertThat(service.reserveDispatch(id)).isZero();
            given(clock.instant()).willReturn(next);
            assertThat(service.findDueRunIds()).contains(id);
            assertThat(service.reserveDispatch(id)).isPositive();
            claim = service.claim(id);
            assertThat(claim.action()).isEqualTo(stage.equals("PHOTO") ? "INSPECT" : "CLASSIFY");
            assertThat(claim.deadline()).isEqualTo(deadline);
            assertThat(getPhase(id, stage).calls()).hasSize(failure);
            startedAt = next;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLASSIFICATION", "PHOTO"})
    @DisplayName("Retry-After 대기 중 동일 결과를 다시 받아도 예약을 앞당기거나 호출 이력을 늘리지 않는다")
    void receive_duplicateDuringRetryAfter_preservesSchedule(String stage) {
        // Given
        UUID id = createAtStage(stage);
        JevJobClaim claim = service.claim(id);
        JevCallResult failure = retryable(claim, 10);
        service.receiveResult(
                id,
                stage,
                failure
        );
        Instant next = getPhase(id, stage).availableAt();
        assertThat(next).isBetween(NOW.plusSeconds(10), NOW.plusSeconds(11));
        // When
        given(clock.instant()).willReturn(NOW.plusSeconds(1));
        service.receiveResult(
                id,
                stage,
                failure
        );
        // Then
        assertThat(service.getRun(id).getDispatchAt()).isEqualTo(next);
        assertThat(service.claim(id).action()).isEqualTo("WAIT");
        assertThat(service.getRun(id).getDispatchAt()).isEqualTo(next);
        assertThat(service.reserveDispatch(id)).isZero();
        assertThat(getPhase(id, stage).calls()).hasSize(1);
        assertThat(getPhase(id, stage).deadline()).isEqualTo(claim.deadline());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLASSIFICATION", "PHOTO"})
    @DisplayName("응답 유실의 WAIT도 재시도 시각에 예약하고 뒤늦은 이전 응답은 새 예약을 바꾸지 않는다")
    void claim_responseLostAndLateResult_preservesNextDispatch(String stage) {
        // Given
        UUID id = createAtStage(stage);
        JevJobClaim first = service.claim(id);
        given(clock.instant()).willReturn(NOW.plusSeconds(45));
        // When
        assertThat(service.claim(id).action()).isEqualTo("WAIT");
        // Then
        assertThat(service.getRun(id).getDispatchAt()).isEqualTo(NOW.plusSeconds(47));
        given(clock.instant()).willReturn(NOW.plusSeconds(47));
        assertThat(service.reserveDispatch(id)).isPositive();
        JevJobClaim second = service.claim(id);
        Instant dispatchAt = service.getRun(id).getDispatchAt();
        given(clock.instant()).willReturn(NOW.plusSeconds(48));
        service.receiveResult(
                id,
                stage,
                success(first,
                        stage.equals("PHOTO") ? photoResponse(false) : classificationResponse())
        );
        assertThat(service.getRun(id).getDispatchAt()).isEqualTo(dispatchAt);
        assertThat(getPhase(id, stage).callId()).isEqualTo(second.callId());
        assertThat(getPhase(id, stage).deadline()).isEqualTo(first.deadline());
        assertThat(getPhase(id, stage).calls()).hasSize(1);
        assertThat(getPhase(id, stage).calls().getFirst().errorCode())
                .isEqualTo("LATE_RESULT_IGNORED");
        assertThat(getPhase(id, stage).calls().getFirst().inputTokens()).isEqualTo(100);
    }

    @Test
    @DisplayName("같은 분류를 기다리는 다른 작업도 WAIT에서 공유 재시도 시각으로 전달을 예약한다")
    void claim_sharedClassificationRetry_usesSharedAvailability() {
        // Given
        UUID first = create();
        UUID second = create();
        JevJobClaim claim = service.claim(first);
        given(clock.instant()).willReturn(NOW.plusSeconds(30));
        service.receiveResult(
                first,
                "CLASSIFICATION",
                retryable(claim, null)
        );
        Instant next = service.getCategory(first).getPhase().availableAt();
        assertThat(service.reserveDispatch(second)).isEqualTo(1);
        // When
        assertThat(service.claim(second).action()).isEqualTo("WAIT");
        // Then
        assertThat(service.getRun(second).getDispatchAt()).isEqualTo(next);
        given(clock.instant()).willReturn(next);
        assertThat(service.reserveDispatch(second)).isEqualTo(2);
        JevJobClaim retry = service.claim(second);
        assertThat(retry.action()).isEqualTo("CLASSIFY");
        assertThat(retry.deadline()).isEqualTo(claim.deadline());
        assertThat(service.claim(first).action()).isEqualTo("WAIT");
        assertThat(service.getCategory(second).getPhase().calls()).hasSize(1);
    }

    private UUID createAtStage(String stage) {
        UUID id = create();
        if (stage.equals("PHOTO")) {
            completeClassification(id);
        }
        return id;
    }

    private JevPhase getPhase(UUID id, String stage) {
        return stage.equals("PHOTO")
                ? service.getRun(id).getPhase()
                : service.getCategory(id).getPhase();
    }

    private JevCallResult retryable(JevJobClaim claim, Integer retryAfterSeconds) {
        return new JevCallResult(
                claim.callId(),
                null,
                null,
                null,
                retryAfterSeconds == null ? "TIMEOUT" : "HTTP_ERROR",
                retryAfterSeconds == null ? null : 429,
                30000L,
                retryAfterSeconds
        );
    }

    private UUID create() {
        return service.createPreview(
                ADMIN,
                POST,
                TOPIC,
                new JevPromptDraft(
                        "jev-1.14.0",
                        JevPromptFixtures.classification(),
                        JevPromptFixtures.photo()
                )
        ).getId();
    }

    private void completeClassification(UUID id) {
        JevJobClaim claim = service.claim(id);
        service.receiveResult(
                id,
                "CLASSIFICATION",
                success(claim, classificationResponse())
        );
    }

    private JevCallResult success(JevJobClaim claim, String raw) {
        return new JevCallResult(
                claim.callId(),
                null,
                null,
                raw,
                null,
                200,
                123L,
                null
        );
    }

    private String classificationResponse() {
        return response(Map.of("topic_category", Map.of(
                "type",
                "choice",
                "choice",
                "subject",
                "confidence",
                1,
                "probabilities",
                Map.of(
                        "subject",
                        1,
                        "general",
                        0
                )
        )));
    }

    private String photoResponse(boolean missing) {
        Map<String, Object> answers = new LinkedHashMap<>();
        for (String name : List.of(
                "topic_fit",
                "image_safety",
                "image_expression"
        )) {
            answers.put(name, Map.of(
                    "type",
                    "choice",
                    "choice",
                    "reject",
                    "confidence",
                    1,
                    "probabilities",
                    Map.of(
                            "approve",
                            0,
                            "human_review",
                            0,
                            "reject",
                            1
                    )
            ));
        }
        if (missing) {
            answers.remove("image_expression");
        }
        return response(answers);
    }

    private String response(Map<String, Object> answers) {
        return mapper.writeValueAsString(Map.of(
                "model",
                "jev-1.14.0",
                "answers",
                answers,
                "usage",
                Map.of(
                        "input_tokens",
                        100,
                        "output_tokens",
                        20
                )
        ));
    }

    private String sign(
            String timestamp,
            String path,
            byte[] body
    ) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = timestamp + "\nPOST\n" + path + "\n" + hash + "\ntest";
        return "v1="
                + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
