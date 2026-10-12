package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
}
