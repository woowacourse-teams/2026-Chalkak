package com.chalkak.backend.jev.service;

import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevSlackDelivery;
import com.chalkak.backend.jev.domain.JevSourcePolicy;
import com.chalkak.backend.jev.repository.JevJobRepository;
import com.chalkak.backend.notification.service.NotificationDeliveryResult;
import com.chalkak.backend.post.domain.Post;
import com.chalkak.backend.post.repository.PostRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JevSlackService {

    private final JevJobRepository jobs;
    private final PostRepository posts;
    private final Clock clock;
    private final Duration lease;

    public JevSlackService(
            JevJobRepository jobs,
            PostRepository posts,
            Clock clock,
            @Value("${chalkak.admin.notification.slack.connect-timeout:2s}") Duration connectTimeout,
            @Value("${chalkak.admin.notification.slack.read-timeout:3s}") Duration readTimeout
    ) {
        this.jobs = jobs;
        this.posts = posts;
        this.clock = clock;
        this.lease = connectTimeout.plus(readTimeout).plusSeconds(10);
    }

    public List<UUID> findDueIds() {
        return jobs.findDueSlackIds(clock.instant());
    }

    @Transactional
    public Claim claim(UUID id) {
        var target = jobs.findTarget(id).orElse(null);
        if (target == null || target.preview()) {
            return null;
        }
        Post post = posts.findByIdForUpdate(target.postId()).orElse(null);
        JevModerationRun run = jobs.findRunForUpdate(id).orElse(null);
        if (run == null || run.getSlackDelivery() == null) {
            return null;
        }
        Instant now = clock.instant();
        JevSlackDelivery phase = run.getSlackDelivery().refresh(now);
        if (phase.dueAt() == null) {
            run.updateSlackDelivery(phase);
            return null;
        }
        if (JevSourcePolicy.findHoldReason(post) != null) {
            run.updateSlackDelivery(phase.skip());
            return null;
        }
        JevSlackDelivery claimed = phase.claim(now, lease);
        run.updateSlackDelivery(claimed);
        if (claimed == phase) {
            return null;
        }
        phase = claimed;
        String recommendation = run.getEvaluation() != null
                && run.getEvaluation().recommendation() == JevDecision.REJECT
                        ? "사진 반려 추천 · 관리자 확인 필요"
                        : "사진 관리자 검토 요청";
        return new Claim(
                id,
                phase.token(),
                run.getPostId(),
                recommendation,
                "주제: " + run.getTopicTitle() + "\n" + summary(run),
                run.getAppliedAt()
        );
    }

    @Transactional
    public void finish(
            UUID id,
            UUID token,
            NotificationDeliveryResult result
    ) {
        JevModerationRun run = jobs.findRunForUpdate(id).orElse(null);
        if (run != null && run.getSlackDelivery() != null) {
            run.updateSlackDelivery(run.getSlackDelivery().finish(
                    token,
                    result,
                    clock.instant()
            ));
        }
    }

    private String summary(JevModerationRun run) {
        String reason = run.getApplicationReason();
        if ("AUTOMATION_DISABLED".equals(reason)) {
            return "자동 검수 꺼짐 · 수동 검토 대기";
        }
        if ("CONFIGURATION_MISSING".equals(reason)) {
            return "프롬프트 미설정 · 수동 검토 대기";
        }
        if ("PREEXISTING_REGISTRATION".equals(reason)) {
            return "도입 전 등록 · 수동 검토 대기";
        }
        if (run.getEvaluation() == null || run.getEvaluation().error() != null) {
            return "최종 기술 오류 · 관리자에서 오류와 재시도 이력 확인";
        }
        Map<String, String> names = Map.of(
                "topic_fit",
                "주제 적합성",
                "image_safety",
                "사진 안전성",
                "image_expression",
                "사진 속 욕설·손짓"
        );
        return run.getEvaluation().decisions().entrySet().stream()
                .filter(entry -> entry.getValue() != JevDecision.APPROVE)
                .map(entry -> names.getOrDefault(entry.getKey(), "사진 검사")
                        + (entry.getValue() == JevDecision.REJECT ? ": 반려 추천" : ": 검토"))
                .sorted().reduce((first, next) -> first + " / " + next)
                .orElse("운영 상태 변경 · 관리자 확인 필요");
    }

    public record Claim(
            UUID runId,
            UUID token,
            UUID postId,
            String title,
            String summary,
            Instant occurredAt
    ) {
    }
}
