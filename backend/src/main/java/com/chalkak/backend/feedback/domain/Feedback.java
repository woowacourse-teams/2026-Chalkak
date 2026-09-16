package com.chalkak.backend.feedback.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

    public static final int MAX_CONTENT_LENGTH = 1000;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Feedback create(UUID userId, String content) {
        Feedback feedback = new Feedback();
        feedback.userId = userId;
        feedback.content = normalizeContent(content);
        return feedback;
    }

    /**
     * 보관할 형태로 다듬은 내용이 길이 제한 안에 있는지 본다.
     *
     * <p>길이를 재는 기준은 앞뒤 공백을 제거한 뒤다. 요청 DTO도 같은 답을 내야 400과 실제 접수
     * 여부가 갈리지 않으므로, 세는 방법을 각자 구현하지 않고 이 메서드 하나를 함께 쓴다.
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
