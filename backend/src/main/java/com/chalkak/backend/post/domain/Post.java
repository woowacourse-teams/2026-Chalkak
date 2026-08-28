package com.chalkak.backend.post.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.photo.domain.Photo;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    public static final int MAX_TITLE_LENGTH = 10;

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "photo_id", nullable = false, unique = true)
    private Photo photo;

    /**
     * 소비한 이미지 업로드. 다른 애그리게이트라 연관관계가 아니라 식별자로 들고 있다. 처리 콜백이 스토리지 키
     * 규칙을 되짚지 않고 게시물을 찾을 수 있어, 키 규칙이 바뀌어도 조용히 매칭에 실패하지 않는다.
     */
    @Column(name = "post_image_upload_id")
    private UUID postImageUploadId;

    @Column(name = "title", length = MAX_TITLE_LENGTH)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "moderation_status", nullable = false, columnDefinition = "moderation_status")
    private ModerationStatus moderationStatus;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Post(
        User author,
        Topic topic,
        Photo photo,
        UUID postImageUploadId,
        String title
    ) {
        this.author = author;
        this.topic = topic;
        this.photo = photo;
        this.postImageUploadId = postImageUploadId;
        this.title = normalizeTitle(title);
        this.moderationStatus = ModerationStatus.VALIDATING;
    }

    public static Post createPost(
        User author,
        Topic topic,
        Photo photo,
        UUID postImageUploadId,
        String title
    ) {
        validateRelations(author, topic, photo);
        if (postImageUploadId == null) {
            throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "게시물 생성 정보가 올바르지 않습니다."
            );
        }
        return new Post(author, topic, photo, postImageUploadId, title);
    }

    public void requestModeration() {
        validateModerationStatus(ModerationStatus.VALIDATING);
        this.moderationStatus = ModerationStatus.PENDING;
    }

    public void approve(Instant moderatedAt) {
        decideModeration(ModerationStatus.APPROVED, moderatedAt);
    }

    public boolean isValidating() {
        return moderationStatus == ModerationStatus.VALIDATING;
    }

    public void reject(Instant moderatedAt) {
        decideModeration(ModerationStatus.REJECTED, moderatedAt);
    }

    public void failImageProcessing() {
        validateModerationStatus(ModerationStatus.VALIDATING);
        this.moderationStatus = ModerationStatus.REJECTED;
        this.moderatedAt = null;
    }

    public void deleteByAuthor(UUID authorId, Instant deletedAt) {
        validateAuthor(authorId);
        validateAuthorDeletionStatus();
        deleteIfNotDeleted(deletedAt);
    }

    public void deleteByAdmin(Instant deletedAt) {
        validateAdminDeletionStatus();
        deleteIfNotDeleted(deletedAt);
    }

    private void validateAdminDeletionStatus() {
        if (moderationStatus == ModerationStatus.VALIDATING) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이미지 처리 중인 게시물은 삭제할 수 없습니다."
            );
        }
    }

    private void validateAuthor(UUID authorId) {
        if (!author.getId().equals(authorId)) {
            throw new ForbiddenException(
                    ErrorCode.FORBIDDEN,
                    "본인의 게시물만 삭제할 수 있습니다."
            );
        }
    }

    private void validateAuthorDeletionStatus() {
        if (moderationStatus == ModerationStatus.REJECTED) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "검수 거절된 게시물은 삭제할 수 없습니다."
            );
        }
        if (moderationStatus == ModerationStatus.VALIDATING) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이미지 처리 중인 게시물은 삭제할 수 없습니다."
            );
        }
    }

    private void deleteIfNotDeleted(Instant deletedAt) {
        if (this.deletedAt != null) {
            return;
        }
        if (deletedAt == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "게시물 삭제 시각이 필요합니다."
            );
        }
        photo.delete(deletedAt);
        this.deletedAt = deletedAt;
    }

    private void decideModeration(ModerationStatus moderationStatus, Instant moderatedAt) {
        validateModerationStatus(ModerationStatus.PENDING);
        if (moderatedAt == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "게시물 검수 시각이 필요합니다."
            );
        }
        this.moderationStatus = moderationStatus;
        this.moderatedAt = moderatedAt;
    }

    private void validateModerationStatus(ModerationStatus expectedStatus) {
        if (moderationStatus != expectedStatus) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "게시물 검수 상태를 변경할 수 없습니다."
            );
        }
    }

    private static void validateRelations(User author, Topic topic, Photo photo) {
        if (author == null || topic == null || photo == null) {
            throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "게시물 생성 정보가 올바르지 않습니다."
            );
        }
    }

    /**
     * 이모지 한 글자는 UTF-16 code unit 두 칸을 쓰므로 {@code String.length()}로 세면 사용자가 입력한 글자 수보다 길게
     * 계산된다. {@code posts.title}이 code point를 세는 {@code VARCHAR(10)}이므로 길이도 code point로 판정한다.
     */
    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        if (title.codePointCount(0, title.length()) > MAX_TITLE_LENGTH) {
            throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "제목은 10자 이하여야 합니다."
            );
        }
        return title;
    }
}
