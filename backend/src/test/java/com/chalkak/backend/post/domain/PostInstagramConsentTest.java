package com.chalkak.backend.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PostInstagramConsentTest {

    private static final Instant NOW = Instant.parse("2026-11-03T09:20:00Z");

    @Test
    @DisplayName("동의하지 않으면 ID와 동의·철회 시각이 없다")
    void of_notAgreed_hasNoIdAndTimestamps() {
        // When
        PostInstagramConsent consent = PostInstagramConsent.of(
                false,
                null,
                NOW
        );

        // Then
        assertThat(consent.agreed()).isFalse();
        assertThat(consent.instagramId()).isNull();
        assertThat(consent.agreedAt()).isNull();
        assertThat(consent.revokedAt()).isNull();
    }

    @Test
    @DisplayName("ID 없이 동의하면 동의 시각을 주어진 시각으로 기록한다")
    void of_agreedWithoutId_recordsAgreedAt() {
        // When
        PostInstagramConsent consent = PostInstagramConsent.of(
                true,
                null,
                NOW
        );

        // Then
        assertThat(consent.agreed()).isTrue();
        assertThat(consent.instagramId()).isNull();
        assertThat(consent.agreedAt()).isEqualTo(NOW);
        assertThat(consent.revokedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"chalkak", "  chalkak  ", "@chalkak", "  @Chalkak  ", "CHALKAK"})
    @DisplayName("앞뒤 공백을 제거하고 맨 앞 @ 하나를 제거한 뒤 소문자로 정규화한다")
    void of_unnormalizedId_normalizesId(String raw) {
        // When
        PostInstagramConsent consent = PostInstagramConsent.of(
                true,
                raw,
                NOW
        );

        // Then
        assertThat(consent.instagramId()).isEqualTo("chalkak");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "@", "  @  ", "\t"})
    @DisplayName("비었거나 @만 있는 ID는 ID 없음으로 본다")
    void of_blankOrAtOnlyId_hasNoId(String raw) {
        // When
        PostInstagramConsent consent = PostInstagramConsent.of(
                true,
                raw,
                NOW
        );

        // Then
        assertThat(consent.agreed()).isTrue();
        assertThat(consent.instagramId()).isNull();
    }

    @Test
    @DisplayName("정규화한 ID가 30자면 허용하고 31자면 거부한다")
    void of_idLengthBoundary_allows30AndRejects31() {
        // Given
        String thirty = "a".repeat(30);
        String thirtyOne = "a".repeat(31);

        // When & Then
        assertThat(PostInstagramConsent.of(
                true,
                "@" + thirty,
                NOW
        ).instagramId()).isEqualTo(thirty);
        assertThat(PostInstagramConsent.isIdFormatValid(thirty)).isTrue();
        assertThat(PostInstagramConsent.isIdFormatValid(thirtyOne)).isFalse();
        assertThatThrownBy(() -> PostInstagramConsent.of(
                true,
                thirtyOne,
                NOW
        )).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"찰칵", "cha-kak", "cha kak", "@@chalkak", "chalkak!", "cha_kak😀"})
    @DisplayName("허용하지 않는 문자가 있으면 ID를 거부한다")
    void of_idWithDisallowedCharacters_throwsBusinessException(String raw) {
        // When & Then
        assertThat(PostInstagramConsent.isIdFormatValid(raw)).isFalse();
        assertThatThrownBy(() -> PostInstagramConsent.of(
                true,
                raw,
                NOW
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("영문 소문자, 숫자, 마침표, 밑줄은 허용한다")
    void of_idWithAllowedCharacters_createsConsent() {
        // When
        PostInstagramConsent consent = PostInstagramConsent.of(
                true,
                "Cha.Kak_01",
                NOW
        );

        // Then
        assertThat(consent.instagramId()).isEqualTo("cha.kak_01");
    }

    @Test
    @DisplayName("동의하지 않았는데 ID가 있으면 거부한다")
    void of_notAgreedWithId_throwsBusinessException() {
        // When & Then
        assertThat(PostInstagramConsent.isIdAllowedWithAgreement(false, "chalkak")).isFalse();
        assertThat(PostInstagramConsent.isIdAllowedWithAgreement(false, "@chalkak")).isFalse();
        assertThatThrownBy(() -> PostInstagramConsent.of(
                false,
                "chalkak",
                NOW
        )).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "@"})
    @DisplayName("동의하지 않아도 비었거나 @만 있는 ID는 ID 없음으로 허용한다")
    void of_notAgreedWithBlankId_hasNoId(String raw) {
        // When
        PostInstagramConsent consent = PostInstagramConsent.of(
                false,
                raw,
                NOW
        );

        // Then
        assertThat(PostInstagramConsent.isIdAllowedWithAgreement(false, raw)).isTrue();
        assertThat(consent.agreed()).isFalse();
        assertThat(consent.instagramId()).isNull();
        assertThat(consent.agreedAt()).isNull();
    }
}
