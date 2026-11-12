package com.chalkak.backend.filter.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "filters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Filter {

    private static final int MAX_NAME_LENGTH = 30;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id", nullable = false, updatable = false)
    private User artist;

    @Column(length = MAX_NAME_LENGTH)
    private String name;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FilterStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Filter createDraft(User artist, Instant now) {
        Filter filter = new Filter();
        filter.artist = artist;
        filter.status = FilterStatus.DRAFT;
        filter.createdAt = now;
        filter.updatedAt = now;
        return filter;
    }

    public void updateDraft(
            String name,
            String description,
            Instant now
    ) {
        validateDraft("초안 상태의 필터만 수정할 수 있습니다.");
        if (name != null) {
            this.name = normalizeText(
                    name,
                    MAX_NAME_LENGTH,
                    "필터 이름"
            );
        }
        if (description != null) {
            this.description = normalizeText(
                    description,
                    MAX_DESCRIPTION_LENGTH,
                    "필터 설명"
            );
        }
        updatedAt = now;
    }

    public void deleteDraft(Instant now) {
        validateDraft("초안 상태의 필터만 삭제할 수 있습니다.");
        if (deletedAt == null) {
            deletedAt = now;
            updatedAt = now;
        }
    }

    public boolean isOwnedBy(UUID userId) {
        return artist.getId().equals(userId);
    }

    private void validateDraft(String message) {
        if (status != FilterStatus.DRAFT) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    private static String normalizeText(
            String value,
            int maxLength,
            String fieldName
    ) {
        String normalized = value.strip();
        if (normalized.indexOf('\0') >= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    fieldName + "에 사용할 수 없는 문자가 포함되어 있습니다.");
        }
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    fieldName + "은 최대 " + maxLength + "자까지 입력할 수 있습니다.");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
