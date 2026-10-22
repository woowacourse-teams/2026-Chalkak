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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReminderTest {

    private static final Instant NOW = Instant.parse("2026-10-21T09:00:00Z");
    private static final String TITLE = "오늘이 얼마 남지 않았어요";
    private static final String BODY = "사진을 자랑해 주세요!";

    @Test
    @DisplayName("리마인더를 생성하면 제목·본문의 앞뒤 공백을 제거하고 반복 방식·대상·발송 시각을 저장한다")
    void create_validInput_normalizesTextAndStoresSchedule() {
        // Given
        Instant scheduledAt = NOW.plus(Duration.ofHours(1));

        // When
        Reminder reminder = Reminder.create(
                "  " + TITLE + "  ",
                "\n" + BODY + " ",
                ReminderRepeat.DAILY,
                ReminderTarget.TOPIC_NON_PARTICIPANTS,
                scheduledAt,
                NOW
        );

        // Then
        assertThat(reminder.getTitle()).isEqualTo(TITLE);
        assertThat(reminder.getBody()).isEqualTo(BODY);
        assertThat(reminder.getRepeat()).isEqualTo(ReminderRepeat.DAILY);
        assertThat(reminder.getTarget()).isEqualTo(ReminderTarget.TOPIC_NON_PARTICIPANTS);
        assertThat(reminder.getScheduledAt()).isEqualTo(scheduledAt);
    }

    @Test
    @DisplayName("제목은 공백 제거 후 50자까지 허용하고 51자는 거절한다")
    void create_titleLengthBoundary_acceptsUpToFifty() {
        // Given
        String fifty = "가".repeat(50);

        // When & Then
        assertThat(createWithText(" " + fifty + " ", BODY).getTitle()).isEqualTo(fifty);
        assertThatThrownBy(() -> createWithText("가".repeat(51), BODY))
                .isInstanceOf(BusinessException.class)
                .hasMessage("리마인더 제목은 1자 이상 50자 이하여야 합니다.");
    }

    @Test
    @DisplayName("본문은 공백 제거 후 500자까지 허용하고 501자는 거절한다")
    void create_bodyLengthBoundary_acceptsUpToFiveHundred() {
        // Given
        String fiveHundred = "가".repeat(500);

        // When & Then
        assertThat(createWithText(TITLE, fiveHundred).getBody()).isEqualTo(fiveHundred);
        assertThatThrownBy(() -> createWithText(TITLE, "가".repeat(501)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("리마인더 본문은 1자 이상 500자 이하여야 합니다.");
    }

    @Test
    @DisplayName("이모지는 UTF-16 길이가 아니라 문자 하나로 센다")
    void create_emojiTitle_countsCodePoints() {
        // Given
        String fiftyEmojis = "📸".repeat(50);

        // When & Then
        assertThat(createWithText(fiftyEmojis, BODY).getTitle()).isEqualTo(fiftyEmojis);
        assertThatThrownBy(() -> createWithText("📸".repeat(51), BODY))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("제목이 없거나 공백뿐이면 거절한다")
    void create_blankTitle_throwsBusinessException(String title) {
        // When & Then
        assertThatThrownBy(() -> createWithText(title, BODY))
                .isInstanceOf(BusinessException.class)
                .hasMessage("리마인더 제목은 1자 이상 50자 이하여야 합니다.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t"})
    @DisplayName("본문이 없거나 공백뿐이면 거절한다")
    void create_blankBody_throwsBusinessException(String body) {
        // When & Then
        assertThatThrownBy(() -> createWithText(TITLE, body))
                .isInstanceOf(BusinessException.class)
                .hasMessage("리마인더 본문은 1자 이상 500자 이하여야 합니다.");
    }

    @Test
    @DisplayName("반복 방식이 없으면 거절한다")
    void create_nullRepeat_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> Reminder.create(
                TITLE,
                BODY,
                null,
                ReminderTarget.ALL,
                NOW,
                NOW
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("리마인더 반복 방식이 필요합니다.");
    }

    @Test
    @DisplayName("발송 대상이 없으면 거절한다")
    void create_nullTarget_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> Reminder.create(
                TITLE,
                BODY,
                ReminderRepeat.ONCE,
                null,
                NOW,
                NOW
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("리마인더 발송 대상이 필요합니다.");
    }

    @Test
    @DisplayName("발송 시각이 없으면 거절한다")
    void create_nullScheduledAt_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> createScheduledAt(ReminderRepeat.ONCE, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("리마인더 발송 시각이 필요합니다.");
    }

    @ParameterizedTest
    @EnumSource(ReminderRepeat.class)
    @DisplayName("반복 방식과 관계없이 현재 시각부터는 허용하고 현재보다 과거는 거절한다")
    void create_pastScheduledAtBoundary_acceptsFromNow(ReminderRepeat repeat) {
        // When & Then
        assertThat(createScheduledAt(repeat, NOW.plusSeconds(1)).getScheduledAt())
                .isEqualTo(NOW.plusSeconds(1));
        assertThat(createScheduledAt(repeat, NOW).getScheduledAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> createScheduledAt(repeat, NOW.minusNanos(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("리마인더 발송 시각은 현재 시각보다 과거일 수 없습니다.");
    }

    @Test
    @DisplayName("발송 시각은 DB 저장 정밀도인 마이크로초까지만 남긴다")
    void create_nanosecondScheduledAt_truncatesToMicros() {
        // Given
        Instant scheduledAt = Instant.parse("2026-10-21T10:00:00.123456789Z");

        // When
        Reminder reminder = createScheduledAt(ReminderRepeat.ONCE, scheduledAt);

        // Then
        assertThat(reminder.getScheduledAt())
                .isEqualTo(Instant.parse("2026-10-21T10:00:00.123456Z"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 50, 51})
    @DisplayName("제목 판단은 리마인더 생성의 제목 검증과 같은 결과를 낸다")
    void isValidTitle_sameAsCreate_returnsConsistentResult(int length) {
        // Given
        String title = " " + "가".repeat(length) + " ";
        boolean creatable = isCreatable(title);

        // When & Then
        assertThat(Reminder.isValidTitle(title)).isEqualTo(creatable);
    }

    @Test
    @DisplayName("리마인더를 수정하면 제목·본문의 앞뒤 공백을 제거하고 반복 방식·대상·발송 시각을 바꾼다")
    void update_validInput_updatesNormalizedContent() {
        // Given
        Reminder reminder = createScheduledAt(ReminderRepeat.ONCE, NOW.plusSeconds(3600));
        Instant newScheduledAt = NOW.plusSeconds(7200);

        // When
        reminder.update(
                " 새 제목 ",
                " 새 본문 ",
                ReminderRepeat.DAILY,
                ReminderTarget.TOPIC_PARTICIPANTS,
                newScheduledAt,
                NOW
        );

        // Then
        assertThat(reminder.getTitle()).isEqualTo("새 제목");
        assertThat(reminder.getBody()).isEqualTo("새 본문");
        assertThat(reminder.getRepeat()).isEqualTo(ReminderRepeat.DAILY);
        assertThat(reminder.getTarget()).isEqualTo(ReminderTarget.TOPIC_PARTICIPANTS);
        assertThat(reminder.getScheduledAt()).isEqualTo(newScheduledAt);
    }

    @Test
    @DisplayName("발송 시각이 지난 일회성 리마인더는 수정할 수 없고 기존 내용을 유지한다")
    void update_sentOnceReminder_throwsBusinessException() {
        // Given
        Reminder reminder = createScheduledAt(ReminderRepeat.ONCE, NOW);
        Instant afterSent = NOW.plusSeconds(60);

        // When & Then
        assertThatThrownBy(() -> reminder.update(
                "새 제목",
                BODY,
                ReminderRepeat.ONCE,
                ReminderTarget.ALL,
                afterSent.plusSeconds(3600),
                afterSent
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("발송 시각이 지난 일회성 리마인더는 수정할 수 없습니다.");
        assertThat(reminder.getTitle()).isEqualTo(TITLE);
    }

    @Test
    @DisplayName("시작일이 지난 매일 반복도 제목·본문·대상만 바꾸면 과거 검증 없이 수정한다")
    void update_startedDailyWithSameSchedule_skipsPastValidation() {
        // Given
        Reminder reminder = createScheduledAt(ReminderRepeat.DAILY, NOW);
        Instant tenDaysLater = NOW.plusSeconds(86_400 * 10);

        // When
        reminder.update(
                "새 제목",
                "새 본문",
                ReminderRepeat.DAILY,
                ReminderTarget.TOPIC_NON_PARTICIPANTS,
                NOW,
                tenDaysLater
        );

        // Then
        assertThat(reminder.getTitle()).isEqualTo("새 제목");
        assertThat(reminder.getTarget()).isEqualTo(ReminderTarget.TOPIC_NON_PARTICIPANTS);
        assertThat(reminder.getScheduledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("시작일이 지난 매일 반복의 발송 시각을 과거로 바꾸면 거절하고 기존 내용을 유지한다")
    void update_dailyScheduleChangedToPast_throwsBusinessException() {
        // Given
        Reminder reminder = createScheduledAt(ReminderRepeat.DAILY, NOW);
        Instant tenDaysLater = NOW.plusSeconds(86_400 * 10);

        // When & Then
        assertThatThrownBy(() -> reminder.update(
                "새 제목",
                BODY,
                ReminderRepeat.DAILY,
                ReminderTarget.ALL,
                NOW.plusSeconds(3600),
                tenDaysLater
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("리마인더 발송 시각은 현재 시각보다 과거일 수 없습니다.");
        assertThat(reminder.getTitle()).isEqualTo(TITLE);
        assertThat(reminder.getScheduledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("반복 방식을 바꾸면 발송 시각이 같아도 과거 검증을 한다")
    void update_repeatChangedWithPastSchedule_throwsBusinessException() {
        // Given
        Reminder reminder = createScheduledAt(ReminderRepeat.DAILY, NOW);
        Instant tenDaysLater = NOW.plusSeconds(86_400 * 10);

        // When & Then
        assertThatThrownBy(() -> reminder.update(
                TITLE,
                BODY,
                ReminderRepeat.ONCE,
                ReminderTarget.ALL,
                NOW,
                tenDaysLater
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("리마인더 발송 시각은 현재 시각보다 과거일 수 없습니다.");
        assertThat(reminder.getRepeat()).isEqualTo(ReminderRepeat.DAILY);
    }

    @Test
    @DisplayName("저장 정밀도 아래 나노초만 다른 발송 시각은 바뀐 일정으로 보지 않는다")
    void update_scheduledAtWithNanosecondsOnly_treatsAsSameSchedule() {
        // Given
        Reminder reminder = createScheduledAt(ReminderRepeat.DAILY, NOW);
        Instant tenDaysLater = NOW.plusSeconds(86_400 * 10);

        // When
        reminder.update(
                "새 제목",
                BODY,
                ReminderRepeat.DAILY,
                ReminderTarget.ALL,
                NOW.plusNanos(999),
                tenDaysLater
        );

        // Then
        assertThat(reminder.getTitle()).isEqualTo("새 제목");
        assertThat(reminder.getScheduledAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @CsvSource(value = {"'', 본문", "제목, ''"})
    @DisplayName("수정 입력은 등록과 같은 기준으로 검증하고 실패하면 기존 내용을 유지한다")
    void update_invalidContent_keepsOriginalContent(String title, String body) {
        // Given
        Reminder reminder = createScheduledAt(ReminderRepeat.ONCE, NOW.plusSeconds(3600));

        // When & Then
        assertThatThrownBy(() -> reminder.update(
                title,
                body,
                ReminderRepeat.DAILY,
                ReminderTarget.TOPIC_PARTICIPANTS,
                NOW.plusSeconds(7200),
                NOW
        ))
                .isInstanceOf(BusinessException.class);
        assertThat(reminder.getTitle()).isEqualTo(TITLE);
        assertThat(reminder.getBody()).isEqualTo(BODY);
        assertThat(reminder.getRepeat()).isEqualTo(ReminderRepeat.ONCE);
        assertThat(reminder.getTarget()).isEqualTo(ReminderTarget.ALL);
        assertThat(reminder.getScheduledAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    @DisplayName("리마인더를 삭제하면 삭제 시각을 마이크로초까지 기록한다")
    void delete_activeReminder_recordsDeletedAt() {
        // Given
        Reminder reminder = createWithText(TITLE, BODY);

        // When
        reminder.delete(Instant.parse("2026-10-21T10:00:00.123456789Z"));

        // Then
        assertThat(reminder.isDeleted()).isTrue();
        assertThat(reminder.getDeletedAt()).isEqualTo(Instant.parse("2026-10-21T10:00:00.123456Z"));
    }

    @Test
    @DisplayName("이미 삭제된 리마인더는 다시 삭제할 수 없고 최초 삭제 시각을 유지한다")
    void delete_deletedReminder_throwsBusinessException() {
        // Given
        Reminder reminder = createWithText(TITLE, BODY);
        reminder.delete(NOW);

        // When & Then
        assertThatThrownBy(() -> reminder.delete(NOW.plusSeconds(60)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 삭제된 리마인더입니다.");
        assertThat(reminder.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("삭제된 리마인더는 수정할 수 없다")
    void update_deletedReminder_throwsBusinessException() {
        // Given
        Reminder reminder = createScheduledAt(ReminderRepeat.DAILY, NOW);
        reminder.delete(NOW);

        // When & Then
        assertThatThrownBy(() -> reminder.update(
                "새 제목",
                BODY,
                ReminderRepeat.DAILY,
                ReminderTarget.ALL,
                NOW,
                NOW
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("삭제된 리마인더는 수정할 수 없습니다.");
        assertThat(reminder.getTitle()).isEqualTo(TITLE);
    }

    @ParameterizedTest
    @CsvSource({"-1, false", "0, true", "1799, true", "1800, false"})
    @DisplayName("일회성은 발송 시각부터 30분 직전까지만 그 시각을 회차로 돌려준다")
    void findDueOccurrence_once_returnsScheduledAtWithinDeadline(long offsetSeconds, boolean due) {
        // Given
        Instant scheduledAt = Instant.parse("2026-10-21T12:00:00Z");
        Reminder reminder = createScheduledAt(ReminderRepeat.ONCE, scheduledAt);

        // When
        Instant occurrence = reminder.findDueOccurrence(scheduledAt.plusSeconds(offsetSeconds));

        // Then
        if (due) {
            assertThat(occurrence).isEqualTo(scheduledAt);
            return;
        }
        assertThat(occurrence).isNull();
    }

    @Test
    @DisplayName("현재 시각으로 등록한 일회성은 바로 회차가 된다")
    void findDueOccurrence_onceScheduledNow_returnsImmediately() {
        // Given
        Reminder reminder = createScheduledAt(ReminderRepeat.ONCE, NOW);

        // When & Then
        assertThat(reminder.findDueOccurrence(NOW.plusSeconds(1))).isEqualTo(NOW);
    }

    @Test
    @DisplayName("매일 반복은 오늘 KST 날짜의 같은 시각을 회차로 돌려준다")
    void findDueOccurrence_dailyAfterStart_returnsTodayOccurrence() {
        // Given
        Reminder reminder = createScheduledAt(
                ReminderRepeat.DAILY,
                Instant.parse("2026-10-21T12:00:00Z")
        );

        // When & Then
        assertThat(reminder.findDueOccurrence(Instant.parse("2026-10-25T12:10:00Z")))
                .isEqualTo(Instant.parse("2026-10-25T12:00:00Z"));
    }

    @Test
    @DisplayName("매일 반복은 시작일 전에는 회차가 없고 시작일 당일부터 회차가 있다")
    void findDueOccurrence_dailyAroundStartDate_startsOnStartDate() {
        // Given
        Reminder reminder = createScheduledAt(
                ReminderRepeat.DAILY,
                Instant.parse("2026-10-22T12:00:00Z")
        );

        // When & Then
        assertThat(reminder.findDueOccurrence(Instant.parse("2026-10-21T12:10:00Z"))).isNull();
        assertThat(reminder.findDueOccurrence(Instant.parse("2026-10-22T12:10:00Z")))
                .isEqualTo(Instant.parse("2026-10-22T12:00:00Z"));
    }

    @Test
    @DisplayName("매일 반복은 회차 시각 뒤에 문구를 고쳐도 그날 회차를 그대로 돌려준다")
    void findDueOccurrence_dailyEditedAfterOccurrence_keepsTodayOccurrence() {
        // Given
        Reminder reminder = createScheduledAt(
                ReminderRepeat.DAILY,
                Instant.parse("2026-10-21T12:00:00Z")
        );
        Instant editedAt = Instant.parse("2026-10-25T12:00:30Z");
        reminder.update(
                "고친 제목",
                "고친 본문",
                ReminderRepeat.DAILY,
                ReminderTarget.ALL,
                Instant.parse("2026-10-21T12:00:00Z"),
                editedAt
        );

        // When & Then
        assertThat(reminder.findDueOccurrence(editedAt.plusSeconds(30)))
                .isEqualTo(Instant.parse("2026-10-25T12:00:00Z"));
    }

    @Test
    @DisplayName("매일 반복은 KST 자정이 지나면 30분 기한이 남아 있어도 전날 회차를 돌려주지 않는다")
    void findDueOccurrence_dailyAfterKstMidnight_skipsPreviousDay() {
        // Given
        Reminder reminder = createScheduledAt(
                ReminderRepeat.DAILY,
                Instant.parse("2026-10-21T14:50:00Z")
        );

        // When & Then
        assertThat(reminder.findDueOccurrence(Instant.parse("2026-10-22T14:55:00Z")))
                .isEqualTo(Instant.parse("2026-10-22T14:50:00Z"));
        assertThat(reminder.findDueOccurrence(Instant.parse("2026-10-22T15:05:00Z"))).isNull();
    }

    @Test
    @DisplayName("삭제된 리마인더는 회차가 없다")
    void findDueOccurrence_deletedReminder_returnsNull() {
        // Given
        Reminder reminder = createScheduledAt(ReminderRepeat.ONCE, NOW);
        reminder.delete(NOW);

        // When & Then
        assertThat(reminder.findDueOccurrence(NOW.plusSeconds(1))).isNull();
    }

    private boolean isCreatable(String title) {
        try {
            createWithText(title, BODY);
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    private Reminder createWithText(String title, String body) {
        return Reminder.create(
                title,
                body,
                ReminderRepeat.ONCE,
                ReminderTarget.ALL,
                NOW,
                NOW
        );
    }

    private Reminder createScheduledAt(ReminderRepeat repeat, Instant scheduledAt) {
        return Reminder.create(
                TITLE,
                BODY,
                repeat,
                ReminderTarget.ALL,
                scheduledAt,
                NOW
        );
    }
}
