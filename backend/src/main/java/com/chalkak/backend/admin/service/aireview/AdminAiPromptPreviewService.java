package com.chalkak.backend.admin.service.aireview;

import com.chalkak.backend.aireview.domain.AiPromptDraft;
import com.chalkak.backend.aireview.domain.AiPromptParameters;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ReasoningEffort;
import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.domain.PhotoReviewOutput;
import com.chalkak.backend.aireview.service.AiReviewGeneration;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Attempt;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Usage;
import com.chalkak.backend.aireview.service.AiReviewGenerationException;
import com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType;
import com.chalkak.backend.aireview.service.AiReviewGenerationRequest;
import com.chalkak.backend.aireview.service.AiReviewGenerationRequestFactory;
import com.chalkak.backend.aireview.service.AiReviewGenerator;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Service;

/**
 * 저장하지 않은 프롬프트 초안을 공개 게시물에 실행해 보는 관리자 미리보기.
 *
 * <p>
 * OpenAI 호출은 수십 초가 걸릴 수 있으므로 이 클래스는 트랜잭션을 열지 않는다. 게시물 조회는 필요한 연관을 fetch join으로
 * 한 번에 읽어 오므로 OpenAI를 호출하는 동안 DB 연결을 잡고 있지 않다. 아무것도 저장하지 않으며 OpenAI 실패는 예외가 아니라
 * 결과로 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class AdminAiPromptPreviewService {

    private static final Logger AI_REVIEW_LOG = LoggerFactory.getLogger("chalkak.aireview");

    private final AiReviewGenerator aiReviewGenerator;
    private final PostRepository postRepository;
    private final AiReviewGenerationRequestFactory requestFactory;

    public PreviewResult preview(UUID adminId, PreviewCommand command) {
        validateAdminId(adminId);
        AiPromptDraft draft = createDraft(command);
        validateModel(draft.model());
        Post post = getVisiblePost(command.postId());
        AiReviewGenerationRequest request = requestFactory.create(draft, post);

        PreviewResult result = generate(request);
        logPreview(
                adminId,
                draft.model(),
                result
        );
        return result;
    }

    private PreviewResult generate(AiReviewGenerationRequest request) {
        try {
            return PreviewResult.fromGeneration(aiReviewGenerator.generate(request));
        } catch (AiReviewGenerationException exception) {
            return PreviewResult.fromFailure(exception);
        }
    }

    private AiPromptDraft createDraft(PreviewCommand command) {
        return new AiPromptDraft(
                command.type(),
                command.systemPrompt(),
                command.userPromptTemplate(),
                command.model(),
                new AiPromptParameters(
                        command.imageDetail(),
                        command.maxOutputTokens(),
                        command.reasoningEffort(),
                        command.distinctivePerspectiveThreshold()
                )
        );
    }

    private void validateModel(String model) {
        if (!aiReviewGenerator.supportsModel(model)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "허용되지 않은 모델입니다.");
        }
    }

    private Post getVisiblePost(UUID postId) {
        return postRepository.findVisibleById(postId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "게시물을 찾을 수 없습니다."));
    }

    private void validateAdminId(UUID adminId) {
        if (adminId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "관리자 식별자가 필요합니다.");
        }
    }

    /**
     * 프롬프트·이미지 URL·주제·피드백은 남기지 않고 값이 있는 항목만 한 줄로 기록한다. OpenAI를 호출한 요청만 기록하며
     * 400/404로 거절한 요청은 기록하지 않는다.
     */
    private void logPreview(
            UUID adminId,
            String model,
            PreviewResult result
    ) {
        LoggingEventBuilder event = AI_REVIEW_LOG.atInfo()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_AI_REVIEW)
                .addKeyValue(LogFields.EVENT, "preview")
                .addKeyValue(LogFields.ADMIN_ID, adminId)
                .addKeyValue("model", model)
                .addKeyValue("succeeded", result.succeeded());
        if (result.failureType() != null) {
            event = event.addKeyValue("failureType", result.failureType());
        }
        if (result.latencyMs() != null) {
            event = event.addKeyValue("latencyMs", result.latencyMs());
        }
        Usage usage = result.usage();
        if (usage != null) {
            event = addIfPresent(
                    event,
                    "inputTokens",
                    usage.inputTokens()
            );
            event = addIfPresent(
                    event,
                    "cachedInputTokens",
                    usage.cachedInputTokens()
            );
            event = addIfPresent(
                    event,
                    "outputTokens",
                    usage.outputTokens()
            );
            event = addIfPresent(
                    event,
                    "reasoningTokens",
                    usage.reasoningTokens()
            );
        }
        event.log("AI 프롬프트 미리보기");
    }

    private LoggingEventBuilder addIfPresent(
            LoggingEventBuilder event,
            String key,
            Integer value
    ) {
        if (value == null) {
            return event;
        }
        return event.addKeyValue(key, value);
    }

    public record PreviewCommand(
            AiPromptType type,
            String systemPrompt,
            String userPromptTemplate,
            String model,
            ImageDetail imageDetail,
            Integer maxOutputTokens,
            ReasoningEffort reasoningEffort,
            Double distinctivePerspectiveThreshold,
            UUID postId
    ) {
    }

    /**
     * 실행 결과. 실패해도 예외가 아니라 이 값으로 돌려주며, 사용량·지연시간·응답 ID는 OpenAI가 응답 본문을 준 경우에만 있다.
     */
    public record PreviewResult(
            boolean succeeded,
            FailureType failureType,
            Boolean retryable,
            AiReviewResult result,
            PhotoReviewOutput output,
            Usage usage,
            Long latencyMs,
            String responseId
    ) {

        private static PreviewResult fromGeneration(AiReviewGeneration generation) {
            Attempt attempt = generation.attempt();
            return new PreviewResult(
                    true,
                    null,
                    null,
                    generation.result(),
                    generation.output(),
                    attempt.usage(),
                    attempt.latency().toMillis(),
                    attempt.responseId()
            );
        }

        private static PreviewResult fromFailure(AiReviewGenerationException exception) {
            Attempt attempt = exception.findAttempt().orElse(null);
            return new PreviewResult(
                    false,
                    exception.getFailureType(),
                    exception.getFailureType().isRetryable(),
                    null,
                    null,
                    attempt == null ? null : attempt.usage(),
                    attempt == null ? null : attempt.latency().toMillis(),
                    attempt == null ? null : attempt.responseId()
            );
        }
    }
}
