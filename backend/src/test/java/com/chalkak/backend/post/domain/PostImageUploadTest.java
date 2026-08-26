package com.chalkak.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostImageUploadTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-20T00:00:00Z");

    private final User user = UserFixture.create();

    @Test
    @DisplayName("발급한 업로드는 ISSUED 상태로 시작하고 claim 유효 시간 뒤에 만료된다")
    void createPostImageUpload_validUser_startsIssued() {
        // When
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);

        // Then
        assertThat(upload.getStatus()).isEqualTo(PostImageUploadStatus.ISSUED);
        assertThat(upload.getExpiresAt())
                .isEqualTo(ISSUED_AT.plus(PostImageUpload.CLAIM_TTL));
        assertThat(upload.getClaimedAt()).isNull();
    }

    @Test
    @DisplayName("처리 완료하면 READY가 되고 메타데이터를 보관한다")
    void completeProcessing_issuedUpload_becomesReady() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);
        Map<String, Object> metadata = Map.of("width", 4032, "height", 3024);

        // When
        upload.completeProcessing(metadata);

        // Then
        assertThat(upload.getStatus()).isEqualTo(PostImageUploadStatus.READY);
        assertThat(upload.getImageMetadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName("처리 실패하면 REJECTED가 되고 사유를 보관한다")
    void failProcessing_issuedUpload_becomesRejected() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);

        // When
        upload.failProcessing("UNSUPPORTED_FORMAT");

        // Then
        assertThat(upload.getStatus()).isEqualTo(PostImageUploadStatus.REJECTED);
        assertThat(upload.getRejectionReason()).isEqualTo("UNSUPPORTED_FORMAT");
    }

    @Test
    @DisplayName("이미 REJECTED면 완료 콜백이 상태를 바꾸지 않는다")
    void completeProcessing_rejectedUpload_keepsRejected() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);
        upload.failProcessing("TOO_LARGE");

        // When
        upload.completeProcessing(Map.of("width", 10));

        // Then
        assertThat(upload.getStatus()).isEqualTo(PostImageUploadStatus.REJECTED);
        assertThat(upload.getImageMetadata()).isNull();
    }

    @Test
    @DisplayName("이미 READY면 실패 콜백이 상태를 바꾸지 않는다")
    void failProcessing_readyUpload_keepsReady() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);
        upload.completeProcessing(Map.of("width", 10));

        // When
        upload.failProcessing("TOO_LARGE");

        // Then
        assertThat(upload.getStatus()).isEqualTo(PostImageUploadStatus.READY);
        assertThat(upload.getRejectionReason()).isNull();
    }

    @Test
    @DisplayName("완료 콜백을 다시 받아도 처음 메타데이터를 유지한다")
    void completeProcessing_readyUpload_keepsFirstMetadata() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);
        upload.completeProcessing(Map.of("width", 10));

        // When
        upload.completeProcessing(Map.of("width", 20));

        // Then
        assertThat(upload.getImageMetadata()).isEqualTo(Map.of("width", 10));
    }

    @Test
    @DisplayName("claim하면 소비 시각을 기록한다")
    void claim_unclaimedUpload_recordsClaimedAt() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);
        Instant claimedAt = ISSUED_AT.plusSeconds(60);

        // When
        upload.claim(claimedAt);

        // Then
        assertThat(upload.getClaimedAt()).isEqualTo(claimedAt);
    }

    @Test
    @DisplayName("이미 claim된 업로드는 다시 claim할 수 없다")
    void claim_claimedUpload_throwsBusinessException() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);
        upload.claim(ISSUED_AT.plusSeconds(60));

        // When & Then
        assertThatThrownBy(() -> upload.claim(ISSUED_AT.plusSeconds(120)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 사용된 사진입니다.");
    }

    @Test
    @DisplayName("만료 시각 직전에는 claim할 수 있다")
    void claim_justBeforeExpiry_succeeds() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);
        Instant claimedAt = upload.getExpiresAt().minusMillis(1);

        // When
        upload.claim(claimedAt);

        // Then
        assertThat(upload.getClaimedAt()).isEqualTo(claimedAt);
    }

    @Test
    @DisplayName("만료 시각부터는 claim할 수 없다")
    void claim_atExpiry_throwsBusinessException() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);

        // When & Then
        assertThatThrownBy(() -> upload.claim(upload.getExpiresAt()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사진 업로드 유효 시간이 지났습니다.");
    }

    @Test
    @DisplayName("처리 실패한 업로드는 거절 사유를 담아 claim을 막는다")
    void claim_rejectedUpload_throwsBusinessExceptionWithReasonMessage() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);
        upload.failProcessing("UNSUPPORTED_FORMAT");

        // When & Then
        assertThatThrownBy(() -> upload.claim(ISSUED_AT.plusSeconds(60)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("WebP 이미지만 업로드할 수 있습니다.");
    }

    @Test
    @DisplayName("알 수 없는 거절 사유는 기본 안내 문구로 claim을 막는다")
    void claim_unknownRejectionReason_throwsBusinessExceptionWithDefaultMessage() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);
        upload.failProcessing("SOMETHING_NEW");

        // When & Then
        assertThatThrownBy(() -> upload.claim(ISSUED_AT.plusSeconds(60)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("처리할 수 없는 사진입니다. 다시 업로드해 주세요.");
    }

    @Test
    @DisplayName("발급 정보가 없으면 업로드를 만들 수 없다")
    void createPostImageUpload_nullUser_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> PostImageUpload.createPostImageUpload(null, ISSUED_AT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("거절 사유가 없는 업로드를 claim하면 기본 안내 문구로 실패한다")
    void claim_rejectedWithoutReason_throwsWithDefaultMessage() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);
        upload.failProcessing(null);

        // When & Then
        assertThatThrownBy(() -> upload.claim(ISSUED_AT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("처리할 수 없는 사진입니다.");
    }

    @Test
    @DisplayName("모르는 거절 사유는 기본 안내 문구로 실패한다")
    void claim_unknownReason_throwsWithDefaultMessage() {
        // Given
        PostImageUpload upload = PostImageUpload.createPostImageUpload(user, ISSUED_AT);
        upload.failProcessing("SOMETHING_NEW");

        // When & Then
        assertThatThrownBy(() -> upload.claim(ISSUED_AT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("처리할 수 없는 사진입니다.");
    }
}
