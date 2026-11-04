package com.chalkak.backend.feedback.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

    public static final int MAX_CONTENT_LENGTH = 1000;

    private static final int MAX_ANSWER_LENGTH = 3000;

    private static final String INVALID_CONTENT_MESSAGE = "피드백 내용이 올바르지 않습니다.";

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "answer_content", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "answered_at")
    private Instant answeredAt;

    public static Feedback create(
            UUID userId,
            String content,
            Instant createdAt
    ) {
        Feedback feedback = new Feedback();
        feedback.userId = userId;
        feedback.content = normalizeContent(content);
        feedback.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        return feedback;
    }

    public void answer(String content, Instant answeredAt) {
        if (isDeleted()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "삭제된 문의에는 답변할 수 없습니다.");
        }
        if (this.answeredAt != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "이미 답변한 문의입니다.");
        }
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > MAX_ANSWER_LENGTH) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "답변은 1~3,000자로 입력해 주세요.");
        }
        this.answerContent = normalized;
        this.answeredAt = answeredAt.truncatedTo(ChronoUnit.MICROS);
    }

    public void delete(Instant deletedAt) {
        if (isDeleted()) {
            return;
        }
        // DB 저장값과 메모리·감사 기록의 삭제 시각을 같은 정밀도로 유지한다.
        this.deletedAt = deletedAt.truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 보관할 형태로 다듬은 내용이 길이 제한 안에 있는지 본다.
     *
     * <p>
     * 길이를 재는 기준은 앞뒤 공백을 제거한 뒤다. 요청 DTO도 같은 답을 내야 400과 실제 접수 여부가 갈리지 않으므로, 세는 방법을 각자
     * 구현하지 않고 이 메서드 하나를 함께 쓴다.
     */
    public static boolean isWithinMaxLength(String content) {
        String normalized = content.strip();
        return normalized.codePointCount(0, normalized.length()) <= MAX_CONTENT_LENGTH;
    }

    private static String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_CONTENT_MESSAGE);
        }
        if (!isWithinMaxLength(content)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_CONTENT_MESSAGE);
        }
        return content.strip();
    }
}
