package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class NoticeTest {

    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    private static final String TITLE = "서비스 점검 안내";
    private static final String BODY = "10월 20일 02:00부터 점검합니다.";

    @Test
    @DisplayName("공지를 생성하면 제목·본문의 앞뒤 공백을 제거하고 예약 시각을 저장한다")
    void create_validInput_normalizesText() {
        // Given
        Instant scheduledAt = NOW.plus(Duration.ofHours(1));

        // When
        Notice notice = Notice.create("  " + TITLE + "  ", "\n" + BODY + " ", scheduledAt, NOW);

        // Then
        assertThat(notice.getTitle()).isEqualTo(TITLE);
        assertThat(notice.getBody()).isEqualTo(BODY);
        assertThat(notice.getScheduledAt()).isEqualTo(scheduledAt);
    }

    @Test
    @DisplayName("제목은 공백 제거 후 50자까지 허용하고 51자는 거절한다")
    void create_titleLengthBoundary_acceptsUpToFifty() {
        // Given
        String fifty = "가".repeat(50);

        // When & Then
        assertThat(Notice.create(" " + fifty + " ", BODY, NOW, NOW).getTitle()).isEqualTo(fifty);
        assertThatThrownBy(() -> Notice.create("가".repeat(51), BODY, NOW, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("공지 제목은 1자 이상 50자 이하여야 합니다.");
    }

    @Test
    @DisplayName("본문은 공백 제거 후 500자까지 허용하고 501자는 거절한다")
    void create_bodyLengthBoundary_acceptsUpToFiveHundred() {
        // Given
        String fiveHundred = "가".repeat(500);

        // When & Then
        assertThat(Notice.create(TITLE, fiveHundred, NOW, NOW).getBody()).isEqualTo(fiveHundred);
        assertThatThrownBy(() -> Notice.create(TITLE, "가".repeat(501), NOW, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("공지 본문은 1자 이상 500자 이하여야 합니다.");
    }

    @Test
    @DisplayName("이모지는 UTF-16 길이가 아니라 문자 하나로 센다")
    void create_emojiTitle_countsCodePoints() {
        // Given
        String fiftyEmojis = "📢".repeat(50);

        // When & Then
        assertThat(Notice.create(fiftyEmojis, BODY, NOW, NOW).getTitle()).isEqualTo(fiftyEmojis);
        assertThatThrownBy(() -> Notice.create("📢".repeat(51), BODY, NOW, NOW))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("제목이 없거나 공백뿐이면 거절한다")
    void create_blankTitle_throwsBusinessException(String title) {
        // When & Then
        assertThatThrownBy(() -> Notice.create(title, BODY, NOW, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("공지 제목은 1자 이상 50자 이하여야 합니다.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("본문이 없거나 공백뿐이면 거절한다")
    void create_blankBody_throwsBusinessException(String body) {
        // When & Then
        assertThatThrownBy(() -> Notice.create(TITLE, body, NOW, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("공지 본문은 1자 이상 500자 이하여야 합니다.");
    }

    @Test
    @DisplayName("예약 시각이 없으면 거절한다")
    void create_nullScheduledAt_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> Notice.create(TITLE, BODY, null, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("공지 예약 시각이 필요합니다.");
    }

    @Test
    @DisplayName("현재보다 정확히 5분 전까지는 허용하고 그보다 과거는 거절한다")
    void create_pastScheduledAtBoundary_acceptsUpToFiveMinutes() {
        // Given
        Instant fiveMinutesAgo = NOW.minus(Duration.ofMinutes(5));

        // When & Then
        assertThat(Notice.create(TITLE, BODY, fiveMinutesAgo.plusSeconds(1), NOW).getScheduledAt())
                .isEqualTo(fiveMinutesAgo.plusSeconds(1));
        assertThat(Notice.create(TITLE, BODY, fiveMinutesAgo, NOW).getScheduledAt())
                .isEqualTo(fiveMinutesAgo);
        assertThatThrownBy(() -> Notice.create(TITLE, BODY, fiveMinutesAgo.minusNanos(1), NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("공지 예약 시각은 현재 시각보다 5분 넘게 과거일 수 없습니다.");
    }

    @Test
    @DisplayName("미래 예약 시각은 기간 제한 없이 허용한다")
    void create_farFutureScheduledAt_accepts() {
        // Given
        Instant nextYear = NOW.plus(Duration.ofDays(365));

        // When
        Notice notice = Notice.create(TITLE, BODY, nextYear, NOW);

        // Then
        assertThat(notice.getScheduledAt()).isEqualTo(nextYear);
    }

    @Test
    @DisplayName("제목 판단은 앞뒤 공백을 제거한 뒤 50자까지 허용하고 51자는 거절한다")
    void isValidTitle_lengthBoundary_acceptsUpToFifty() {
        // When & Then
        assertThat(Notice.isValidTitle(" " + "가".repeat(50) + " ")).isTrue();
        assertThat(Notice.isValidTitle("가".repeat(51))).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("제목 판단은 없거나 공백뿐인 값을 거절한다")
    void isValidTitle_blankTitle_returnsFalse(String title) {
        // When & Then
        assertThat(Notice.isValidTitle(title)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 50, 51})
    @DisplayName("제목 판단은 공지 생성의 제목 검증과 같은 결과를 낸다")
    void isValidTitle_sameAsCreate_returnsConsistentResult(int length) {
        // Given
        String title = "가".repeat(length);
        boolean created = canCreateWithTitle(title);

        // When & Then
        assertThat(Notice.isValidTitle(title)).isEqualTo(created);
    }

    @Test
    @DisplayName("공지를 생성하면 예약 상태이고 배포 시각이 없다")
    void create_validInput_startsScheduled() {
        // When
        Notice notice = Notice.create(TITLE, BODY, NOW, NOW);

        // Then
        assertThat(notice.getStatus()).isEqualTo(NoticeStatus.SCHEDULED);
        assertThat(notice.getDistributedAt()).isNull();
    }

    @Test
    @DisplayName("예약 상태의 공지를 배포하면 배포 상태와 배포 시각을 기록한다")
    void distribute_scheduledNotice_marksDistributed() {
        // Given
        Notice notice = Notice.create(TITLE, BODY, NOW, NOW);
        Instant distributedAt = NOW.plusSeconds(60);

        // When
        notice.distribute(distributedAt);

        // Then
        assertThat(notice.getStatus()).isEqualTo(NoticeStatus.DISTRIBUTED);
        assertThat(notice.getDistributedAt()).isEqualTo(distributedAt);
    }

    @Test
    @DisplayName("이미 배포한 공지는 다시 배포할 수 없고 최초 배포 시각을 유지한다")
    void distribute_distributedNotice_throwsBusinessException() {
        // Given
        Notice notice = Notice.create(TITLE, BODY, NOW, NOW);
        notice.distribute(NOW);

        // When & Then
        assertThatThrownBy(() -> notice.distribute(NOW.plusSeconds(60)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("예약 상태의 공지만 배포할 수 있습니다.");
        assertThat(notice.getDistributedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("예약 시각은 DB 저장 정밀도인 마이크로초까지만 남긴다")
    void create_nanosecondScheduledAt_truncatesToMicros() {
        // Given
        Instant scheduledAt = NOW.plusNanos(1_234_567);

        // When
        Notice notice = Notice.create(TITLE, BODY, scheduledAt, NOW);

        // Then
        assertThat(notice.getScheduledAt()).isEqualTo(NOW.plusNanos(1_234_000));
    }

    @Test
    @DisplayName("예약 상태의 공지는 제목·본문의 앞뒤 공백을 제거해 수정하고 예약 시각을 바꾼다")
    void update_scheduledNotice_updatesNormalizedContent() {
        // Given
        Notice notice = Notice.create(TITLE, BODY, NOW, NOW);
        Instant scheduledAt = NOW.plus(Duration.ofDays(1));

        // When
        notice.update(" 수정한 안내 ", "\n수정한 본문 ", scheduledAt, NOW);

        // Then
        assertThat(notice.getTitle()).isEqualTo("수정한 안내");
        assertThat(notice.getBody()).isEqualTo("수정한 본문");
        assertThat(notice.getScheduledAt()).isEqualTo(scheduledAt);
    }

    @Test
    @DisplayName("배포된 공지는 수정할 수 없고 내용을 유지한다")
    void update_distributedNotice_throwsBusinessException() {
        // Given
        Notice notice = Notice.create(TITLE, BODY, NOW, NOW);
        notice.distribute(NOW);

        // When & Then
        assertThatThrownBy(() -> notice.update("수정한 안내", BODY, NOW, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("배포된 공지는 수정할 수 없습니다.");
        assertThat(notice.getTitle()).isEqualTo(TITLE);
    }

    @ParameterizedTest
    @CsvSource({
            "' ', 본문, 0, 공지 제목은 1자 이상 50자 이하여야 합니다.",
            "제목, ' ', 0, 공지 본문은 1자 이상 500자 이하여야 합니다.",
            "제목, 본문, 301, 공지 예약 시각은 현재 시각보다 5분 넘게 과거일 수 없습니다."})
    @DisplayName("수정 입력은 작성과 같은 기준으로 검증하고 실패하면 기존 내용을 유지한다")
    void update_invalidContent_keepsOriginalContent(
            String title,
            String body,
            long secondsAgo,
            String message
    ) {
        // Given
        Notice notice = Notice.create(TITLE, BODY, NOW, NOW);

        // When & Then
        assertThatThrownBy(() -> notice.update(title, body, NOW.minusSeconds(secondsAgo), NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage(message);
        assertThat(notice.getTitle()).isEqualTo(TITLE);
        assertThat(notice.getBody()).isEqualTo(BODY);
        assertThat(notice.getScheduledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("예약 상태의 공지를 삭제하면 삭제 시각을 마이크로초까지 기록하고 예약 상태를 유지한다")
    void delete_scheduledNotice_recordsDeletedAt() {
        // Given
        Notice notice = Notice.create(TITLE, BODY, NOW, NOW);

        // When
        notice.delete(NOW.plusNanos(1_234_567));

        // Then
        assertThat(notice.isDeleted()).isTrue();
        assertThat(notice.getDeletedAt()).isEqualTo(NOW.plusNanos(1_234_000));
        assertThat(notice.getStatus()).isEqualTo(NoticeStatus.SCHEDULED);
    }

    @Test
    @DisplayName("배포된 공지도 삭제할 수 있고 배포 시각을 유지한다")
    void delete_distributedNotice_recordsDeletedAt() {
        // Given
        Notice notice = Notice.create(TITLE, BODY, NOW, NOW);
        notice.distribute(NOW);

        // When
        notice.delete(NOW.plusSeconds(60));

        // Then
        assertThat(notice.getDeletedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(notice.getDistributedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이미 삭제된 공지는 다시 삭제할 수 없고 최초 삭제 시각을 유지한다")
    void delete_deletedNotice_throwsBusinessException() {
        // Given
        Notice notice = Notice.create(TITLE, BODY, NOW, NOW);
        notice.delete(NOW);

        // When & Then
        assertThatThrownBy(() -> notice.delete(NOW.plusSeconds(60)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 삭제된 공지입니다.");
        assertThat(notice.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("삭제된 공지는 수정할 수 없다")
    void update_deletedNotice_throwsBusinessException() {
        // Given
        Notice notice = Notice.create(TITLE, BODY, NOW, NOW);
        notice.delete(NOW);

        // When & Then
        assertThatThrownBy(() -> notice.update("수정한 안내", BODY, NOW, NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("삭제된 공지는 수정할 수 없습니다.");
    }

    @Test
    @DisplayName("삭제된 공지는 배포할 수 없다")
    void distribute_deletedNotice_throwsBusinessException() {
        // Given
        Notice notice = Notice.create(TITLE, BODY, NOW, NOW);
        notice.delete(NOW);

        // When & Then
        assertThatThrownBy(() -> notice.distribute(NOW))
                .isInstanceOf(BusinessException.class)
                .hasMessage("삭제된 공지는 배포할 수 없습니다.");
        assertThat(notice.getDistributedAt()).isNull();
    }

    private boolean canCreateWithTitle(String title) {
        try {
            Notice.create(title, BODY, NOW, NOW);
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }
}
