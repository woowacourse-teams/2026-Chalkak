package com.chalkak.backend.jev.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.jev.domain.JevCallResult;
import com.chalkak.backend.jev.domain.JevChoiceQuestion;
import com.chalkak.backend.jev.domain.JevEvaluation;
import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevPhase;
import com.chalkak.backend.jev.domain.JevPromptDraft;
import com.chalkak.backend.jev.domain.JevTopicCategory;
import com.chalkak.backend.jev.domain.JevSourcePolicy;
import com.chalkak.backend.jev.repository.JevJobRepository;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JevJobService {

    private static final long RETRY_JITTER_BOUND_MILLIS = 1001;

    private final JevJobRepository jobs;
    private final PostRepository posts;
    private final TopicRepository topics;
    private final JevResponseParser parser;
    private final Clock clock;
    private final JevClassificationKeyFactory keys;
    private final JevAutomationService automation;

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
        if (run.getClassificationId() == null) {
            return null;
        }
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
        if (!run.hasDispatchCapacity()) {
            // 공유 분류의 진행 중 호출도 기존 기한까지 기다린다. 새 전달이나 모델 호출은 늘리지 않는다.
            JevPhase classification = lockCategory(run.getClassificationId()).getPhase();
            if (!classification.completed() && classification.deadline() != null
                    && now.isBefore(classification.deadline())) {
                run.postponeDispatch(classification.deadline());
                return 0;
            }
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
        Execution execution = lockExecution(id);
        JevModerationRun run = execution.run();
        if (run.getFinishedAt() != null) {
            return JevJobClaim.idle("STOP", id);
        }
        if (!run.isPreview()
                && "IMAGE_NOT_READY".equals(JevSourcePolicy.findHoldReason(execution.post()))) {
            return JevJobClaim.idle("WAIT", id);
        }
        Instant now = clock.instant();
        closeIfHeld(execution, now);
        if (run.getFinishedAt() != null) {
            return JevJobClaim.idle("STOP", id);
        }
        run.acknowledgeDispatch();
        JevTopicCategory category = lockCategory(run.getClassificationId());
        category.updatePhase(category.getPhase().refresh(now));
        if (!category.getPhase().completed()) {
            if (!category.getPhase().canClaim(now)) {
                return waitForPhase(
                        run,
                        category.getPhase(),
                        now
                );
            }
            category.updatePhase(category.getPhase().claim(now));
            return claimResponse(
                    run,
                    category,
                    category.getPhase(),
                    "CLASSIFY"
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
            return waitForPhase(
                    run,
                    run.getPhase(),
                    now
            );
        }
        run.updatePhase(run.getPhase().claim(now));
        return claimResponse(
                run,
                category,
                run.getPhase(),
                "INSPECT"
        );
    }

    @Transactional
    public void receiveResult(
            UUID id,
            String stage,
            JevCallResult result
    ) {
        Execution execution = lockExecution(id);
        JevModerationRun run = execution.run();
        Instant now = clock.instant();
        if ("CLASSIFICATION".equals(stage)) {
            receiveClassificationResult(
                    run,
                    result,
                    now
            );
            closeIfHeld(execution, now);
            return;
        }
        if (!"PHOTO".equals(stage)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Jev 호출 단계가 올바르지 않습니다.");
        }
        receivePhotoResult(
                run,
                result,
                now
        );
        closeIfHeld(execution, now);
    }

    private void receiveClassificationResult(
            JevModerationRun run,
            JevCallResult result,
            Instant now
    ) {
        JevTopicCategory category = lockCategory(run.getClassificationId());
        JevPhase previous = category.getPhase();
        JevPhase received = receivePhaseResult(
                previous,
                result,
                now
        );
        category.updatePhase(received);
        if (isSuccessfulCurrentResult(
                result,
                previous,
                received
        )) {
            var draft = automation.resolve(run);
            category.complete(parser.parse(
                    result.rawResponse(),
                    draft.model(),
                    draft.classification().questions()
            ));
        }
        if (result.callId().equals(previous.callId())) {
            run.postponeDispatch(category.getPhase().availableAt());
        }
    }

    private void receivePhotoResult(
            JevModerationRun run,
            JevCallResult result,
            Instant now
    ) {
        JevPhase previous = run.getPhase();
        JevPhase received = receivePhaseResult(
                previous,
                result,
                now
        );
        run.updatePhase(received);
        if (isSuccessfulCurrentResult(
                result,
                previous,
                received
        )) {
            var draft = automation.resolve(run);
            JevEvaluation evaluation = parser.parse(
                    result.rawResponse(),
                    draft.model(),
                    draft.photo().questions()
            );
            run.complete(evaluation, now);
            return;
        }
        if (received.completed() && run.getFinishedAt() == null) {
            run.complete(failedEvaluation(received.failure()), now);
        }
        if (result.callId().equals(previous.callId())) {
            run.postponeDispatch(received.availableAt());
        }
    }

    private JevPhase receivePhaseResult(
            JevPhase previous,
            JevCallResult result,
            Instant now
    ) {
        return previous.receive(
                normalizeResult(
                        result,
                        previous,
                        now
                ),
                now,
                ThreadLocalRandom.current().nextLong(RETRY_JITTER_BOUND_MILLIS)
        );
    }

    private boolean isSuccessfulCurrentResult(
            JevCallResult result,
            JevPhase previous,
            JevPhase received
    ) {
        return received.completed() && received.failure() == null && result.errorCode() == null
                && result.callId().equals(previous.callId());
    }

    private JevJobClaim waitForPhase(
            JevModerationRun run,
            JevPhase phase,
            Instant now
    ) {
        // 일찍 도착한 큐 메시지나 응답 유실 확인도 실제 재시도 가능 시각으로 전달을 예약한다.
        if (phase.callId() == null && phase.availableAt().isAfter(now)) {
            run.postponeDispatch(phase.availableAt());
        }
        return JevJobClaim.idle("WAIT", run.getId());
    }

    private JevModerationRun lockRun(UUID id) {
        return jobs.findRunForUpdate(id).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "Jev 검수 작업을 찾을 수 없습니다."));
    }

    private Execution lockExecution(UUID id) {
        var target = jobs.findTarget(id).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "Jev 검수 작업을 찾을 수 없습니다."));
        Post post = target.preview() ? null : posts.findByIdForUpdate(target.postId()).orElse(null);
        return new Execution(lockRun(id), post);
    }

    private void closeIfHeld(Execution execution, Instant now) {
        JevModerationRun run = execution.run();
        if (run.isPreview()) {
            return;
        }
        String sourceReason = JevSourcePolicy.findHoldReason(execution.post());
        if ("IMAGE_NOT_READY".equals(sourceReason)) {
            return;
        }
        if (sourceReason != null) {
            run.suspend(sourceReason, now);
            return;
        }
        String reason = automation.findHoldReason(run);
        if (reason != null) {
            run.suspend(reason, now);
        }
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
        var usage = parser.readUsage(result.rawResponse());
        return new JevCallResult(
                result.callId(),
                startedAt,
                now,
                result.rawResponse(),
                result.errorCode(),
                result.httpStatus(),
                result.latencyMs(),
                result.retryAfterSeconds(),
                usage.inputTokens(),
                usage.outputTokens()
        );
    }

    private JevTopicCategory lockCategory(UUID id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "분류 호출이 없는 검수 작업입니다.");
        }
        return jobs.findCategoryForUpdate(id).orElseThrow(() -> new NotFoundException(
                ErrorCode.BUSINESS_ERROR, "Jev 분류 작업을 찾을 수 없습니다."));
    }

    private JevJobClaim claimResponse(
            JevModerationRun run,
            JevTopicCategory category,
            JevPhase phase,
            String action
    ) {
        var draft = automation.resolve(run);
        boolean inspectPhoto = "INSPECT".equals(action);
        var questions = inspectPhoto
                ? createPhotoQuestions(draft, category.getCategory())
                : draft.classification().questions();
        return new JevJobClaim(
                action,
                run.getId(),
                phase.callId(),
                phase.deadline(),
                draft.model(),
                run.getTopicTitle(),
                category.getCategory(),
                questions,
                inspectPhoto ? run.getStorageKey() : null
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
        return keys.create(
                topic.getId(),
                topic.getTitle(),
                draft.model(),
                draft.classification(),
                "preview"
        );
    }

    private JevEvaluation failedEvaluation(String error) {
        return JevEvaluation.review(error == null ? "INVALID_RESPONSE" : error);
    }

    private record Execution(JevModerationRun run, Post post) {
    }
}
