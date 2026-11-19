package com.chalkak.backend.jev.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.jev.domain.JevCallResult;
import com.chalkak.backend.jev.domain.JevChoiceQuestion;
import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevEvaluation;
import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevPhase;
import com.chalkak.backend.jev.domain.JevPromptDraft;
import com.chalkak.backend.jev.domain.JevTopicCategory;
import com.chalkak.backend.jev.repository.JevJobRepository;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JevJobService {

    private final JevJobRepository jobs;
    private final PostRepository posts;
    private final TopicRepository topics;
    private final JevResponseParser parser;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public JevModerationRun createPreview(
            UUID adminId,
            UUID postId,
            UUID topicId,
            JevPromptDraft draft
    ) {
        if (draft == null || adminId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "시험 실행 초안이 필요합니다.");
        }
        try {
            draft.validate(adminId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "분류와 사진 카테고리 규칙이 맞지 않습니다.");
        }
        Post post = posts.findVisibleById(postId).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "시험할 공개 게시물을 찾을 수 없습니다."));
        Topic topic = topics.findActiveById(topicId).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "시험할 주제를 찾을 수 없습니다."));
        Instant now = clock.instant();
        String key = classificationKey(topic, draft);
        jobs.lockClassificationKey(key);
        JevTopicCategory category = jobs.findCategoryByKey(key)
                .orElseGet(() -> jobs.saveCategory(JevTopicCategory.create(
                        topicId,
                        key,
                        now
                )));
        return jobs.saveRun(JevModerationRun.preview(
                postId,
                topicId,
                topic.getTitle(),
                post.getPhoto().getOriginalStorageKey(),
                adminId,
                category.getId(),
                draft,
                now
        ));
    }

    public JevModerationRun getRun(UUID id) {
        return jobs.findRun(id).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "Jev 검수 작업을 찾을 수 없습니다."));
    }

    public JevTopicCategory getCategory(UUID id) {
        // 조회 상세도 분류와 사진 호출을 분리해 공유 토큰을 중복 합산하지 않는다.
        JevModerationRun run = getRun(id);
        return jobs.findCategory(run.getClassificationId()).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "Jev 주제 분류를 찾을 수 없습니다."));
    }

    public List<UUID> findDueRunIds() {
        return jobs.findDueRunIds(clock.instant());
    }

    @Transactional
    public int reserveDispatch(UUID id) {
        JevModerationRun run = lockRun(id);
        Instant now = clock.instant();
        if (run.getFinishedAt() != null || run.getDispatchAt().isAfter(now)) {
            return 0;
        }
        return run.reserveDispatch(now);
    }

    @Transactional
    public void recordDispatch(
            UUID id,
            int attempt,
            String error
    ) {
        lockRun(id).recordDispatch(attempt, error);
    }

    @Transactional
    public JevJobClaim claim(UUID id) {
        JevModerationRun run = lockRun(id);
        if (run.getFinishedAt() != null) {
            return JevJobClaim.idle("STOP", id);
        }
        Instant now = clock.instant();
        JevTopicCategory category = lockCategory(run.getClassificationId());
        category.updatePhase(category.getPhase().refresh(now));
        if (!category.getPhase().completed()) {
            if (!category.getPhase().canClaim(now)) {
                return JevJobClaim.idle("WAIT", id);
            }
            category.updatePhase(category.getPhase().claim(now));
            return claimResponse(
                    run,
                    category,
                    category.getPhase(),
                    "CLASSIFY",
                    run.getPreviewDraft().classification().questions()
            );
        }
        if (category.getPhase().failure() != null || category.getCategory() == null) {
            run.complete(failedEvaluation("CLASSIFICATION_FAILED"), now);
            return JevJobClaim.idle("STOP", id);
        }
        run.updatePhase(run.getPhase().refresh(now));
        if (run.getPhase().completed()) {
            run.complete(failedEvaluation(run.getPhase().failure()), now);
            return JevJobClaim.idle("STOP", id);
        }
        if (!run.getPhase().canClaim(now)) {
            return JevJobClaim.idle("WAIT", id);
        }
        run.updatePhase(run.getPhase().claim(now));
        return claimResponse(
                run,
                category,
                run.getPhase(),
                "INSPECT",
                createPhotoQuestions(run.getPreviewDraft(), category.getCategory())
        );
    }

    @Transactional
    public void receiveResult(
            UUID id,
            String stage,
            JevCallResult result
    ) {
        JevModerationRun run = lockRun(id);
        Instant now = clock.instant();
        if ("CLASSIFICATION".equals(stage)) {
            JevTopicCategory category = lockCategory(run.getClassificationId());
            JevPhase previous = category.getPhase();
            result = normalizeResult(
                    result,
                    previous,
                    now
            );
            category.updatePhase(previous.receive(
                    result,
                    now,
                    ThreadLocalRandom.current().nextLong(1001)
            ));
            if (result.errorCode() == null && result.callId().equals(previous.callId())
                    && category.getPhase().failure() == null) {
                category.complete(parser.parse(
                        result.rawResponse(),
                        run.getPreviewDraft().model(),
                        run.getPreviewDraft().classification().questions()
                ));
            }
            run.postponeDispatch(now);
            return;
        }
        if (!"PHOTO".equals(stage)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Jev 호출 단계가 올바르지 않습니다.");
        }
        JevPhase previous = run.getPhase();
        result = normalizeResult(
                result,
                previous,
                now
        );
        run.updatePhase(previous.receive(
                result,
                now,
                ThreadLocalRandom.current().nextLong(1001)
        ));
        if (run.getFinishedAt() == null && run.getPhase().completed()) {
            if (result.errorCode() == null && result.callId().equals(previous.callId())
                    && run.getPhase().failure() == null) {
                JevEvaluation evaluation = parser.parse(
                        result.rawResponse(),
                        run.getPreviewDraft().model(),
                        run.getPreviewDraft().photo().questions()
                );
                run.complete(evaluation, now);
                return;
            }
            run.complete(failedEvaluation(run.getPhase().failure()), now);
        }
        run.postponeDispatch(now);
    }

    private JevModerationRun lockRun(UUID id) {
        return jobs.findRunForUpdate(id).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "Jev 검수 작업을 찾을 수 없습니다."));
    }

    private JevCallResult normalizeResult(
            JevCallResult result,
            JevPhase phase,
            Instant now
    ) {
        Instant startedAt = phase.claimedAt();
        if (!result.callId().equals(phase.callId())) {
            startedAt = phase.calls().stream().filter(call -> call.callId().equals(result.callId()))
                    .findFirst().map(JevCallResult::startedAt)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.BUSINESS_ERROR, "발급된 Jev 호출 식별자가 아닙니다."));
        }
        return new JevCallResult(
                result.callId(),
                startedAt,
                now,
                result.rawResponse(),
                result.errorCode(),
                result.httpStatus(),
                result.latencyMs(),
                result.retryAfterSeconds()
        );
    }

    private JevTopicCategory lockCategory(UUID id) {
        return jobs.findCategoryForUpdate(id).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "Jev 분류 작업을 찾을 수 없습니다."));
    }

    private JevJobClaim claimResponse(
            JevModerationRun run,
            JevTopicCategory category,
            JevPhase phase,
            String action,
            Map<String, JevChoiceQuestion> questions
    ) {
        return new JevJobClaim(
                action,
                run.getId(),
                phase.callId(),
                phase.deadline(),
                run.getPreviewDraft().model(),
                run.getTopicTitle(),
                category.getCategory(),
                questions,
                "INSPECT".equals(action) ? run.getStorageKey() : null
        );
    }

    private Map<String, JevChoiceQuestion> createPhotoQuestions(JevPromptDraft draft,
            String category) {
        Map<String, JevChoiceQuestion> questions = new LinkedHashMap<>(draft.photo().questions());
        String rule = draft.photo().categoryRules().get(category);
        if (rule == null) {
            rule = draft.photo().categoryRules().get("general");
        }
        JevChoiceQuestion topic = questions.get("topic_fit");
        String instructions = draft.photo().categoryInstructions()
                .getOrDefault(category, topic.instructions());
        questions.put("topic_fit", new JevChoiceQuestion(
                topic.type(),
                instructions + "\n\n" + rule,
                topic.criteria()
        ));
        return questions;
    }

    private String classificationKey(Topic topic, JevPromptDraft draft) {
        return classificationKey(
                topic.getId(),
                topic.getTitle(),
                draft
        );
    }

    private String classificationKey(
            UUID topicId,
            String title,
            JevPromptDraft draft
    ) {
        String configuration = objectMapper.writer()
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(draft.classification());
        String content = topicId + "\n" + title + "\n" + draft.model() + "\n" + configuration;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private JevEvaluation failedEvaluation(String error) {
        return new JevEvaluation(
                Map.of(),
                Map.of(),
                JevDecision.HUMAN_REVIEW,
                null,
                null,
                null,
                error == null ? "INVALID_RESPONSE" : error
        );
    }
}
