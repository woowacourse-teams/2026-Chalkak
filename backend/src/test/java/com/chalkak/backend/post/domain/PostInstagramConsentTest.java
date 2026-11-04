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

    private static final Instant CREATED_AT = Instant.parse("2026-11-03T08:00:00Z");
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
    @ValueSource(strings = {"찰칵", "cha-kak", "cha kak", "@@chalkak", "chalkak!", "cha_kak😀",
            "\u212Achalkak", "\u0130d"})
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

    @Test
    @DisplayName("미동의에서 동의로 바꾸면 동의 시각을 교체 시각으로 기록한다")
    void replace_notAgreedToAgreed_recordsAgreedAt() {
        // Given
        PostInstagramConsent before = PostInstagramConsent.of(
                false,
                null,
                CREATED_AT
        );

        // When
        PostInstagramConsent after = before.replace(
                true,
                " @Chalkak ",
                NOW
        );

        // Then
        assertThat(after.agreed()).isTrue();
        assertThat(after.instagramId()).isEqualTo("chalkak");
        assertThat(after.agreedAt()).isEqualTo(NOW);
        assertThat(after.revokedAt()).isNull();
    }

    @Test
    @DisplayName("동의에서 미동의로 바꾸면 철회 시각을 기록하고 ID를 지우며 동의 시각은 남긴다")
    void replace_agreedToNotAgreed_recordsRevokedAtAndClearsId() {
        // Given
        PostInstagramConsent before = PostInstagramConsent.of(
                true,
                "chalkak",
                CREATED_AT
        );

        // When
        PostInstagramConsent after = before.replace(
                false,
                null,
                NOW
        );

        // Then
        assertThat(after.agreed()).isFalse();
        assertThat(after.instagramId()).isNull();
        assertThat(after.agreedAt()).isEqualTo(CREATED_AT);
        assertThat(after.revokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("철회했다가 다시 동의하면 동의 시각만 새로 기록하고 이전 철회 시각은 이력으로 남긴다")
    void replace_reAgreedAfterRevoke_recordsNewAgreedAtAndKeepsRevokedAt() {
        // Given
        Instant revokedAt = Instant.parse("2026-11-03T10:00:00Z");
        PostInstagramConsent revoked = PostInstagramConsent
                .of(
                        true,
                        null,
                        CREATED_AT
                )
                .replace(
                        false,
                        null,
                        revokedAt
                );

        // When
        PostInstagramConsent after = revoked.replace(
                true,
                null,
                NOW
        );

        // Then
        assertThat(after.agreed()).isTrue();
        assertThat(after.agreedAt()).isEqualTo(NOW);
        assertThat(after.revokedAt()).isEqualTo(revokedAt);
    }

    @Test
    @DisplayName("같은 동의값으로 다시 교체해도 동의·철회 시각은 바뀌지 않는다")
    void replace_sameAgreement_keepsTimestamps() {
        // Given
        PostInstagramConsent agreed = PostInstagramConsent.of(
                true,
                null,
                CREATED_AT
        );
        PostInstagramConsent notAgreed = PostInstagramConsent
                .of(
                        true,
                        null,
                        CREATED_AT
                )
                .replace(
                        false,
                        null,
                        NOW
                );

        // When
        PostInstagramConsent agreedAgain = agreed.replace(
                true,
                null,
                NOW.plusSeconds(60)
        );
        PostInstagramConsent notAgreedAgain = notAgreed.replace(
                false,
                "@",
                NOW.plusSeconds(60)
        );

        // Then
        assertThat(agreedAgain).isEqualTo(agreed);
        assertThat(notAgreedAgain).isEqualTo(notAgreed);
    }

    @Test
    @DisplayName("동의를 유지한 채 ID만 바꾸거나 지워도 시각은 바뀌지 않는다")
    void replace_agreedIdOnlyChange_keepsTimestamps() {
        // Given
        PostInstagramConsent before = PostInstagramConsent.of(
                true,
                "chalkak",
                CREATED_AT
        );

        // When
        PostInstagramConsent changed = before.replace(
                true,
                "@New.Id",
                NOW
        );
        PostInstagramConsent removed = changed.replace(
                true,
                null,
                NOW.plusSeconds(60)
        );

        // Then
        assertThat(changed.instagramId()).isEqualTo("new.id");
        assertThat(changed.agreedAt()).isEqualTo(CREATED_AT);
        assertThat(changed.revokedAt()).isNull();
        assertThat(removed.instagramId()).isNull();
        assertThat(removed.agreedAt()).isEqualTo(CREATED_AT);
        assertThat(removed.revokedAt()).isNull();
    }

    @Test
    @DisplayName("교체할 때도 미동의에 ID가 있거나 형식이 올바르지 않으면 거부한다")
    void replace_invalidInput_throwsBusinessException() {
        // Given
        PostInstagramConsent before = PostInstagramConsent.of(
                true,
                "chalkak",
                CREATED_AT
        );

        // When & Then
        assertThatThrownBy(() -> before.replace(
                false,
                "chalkak",
                NOW
        )).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> before.replace(
                true,
                "cha-kak",
                NOW
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("생성자에 대문자가 섞인 ID가 직접 오면 거부한다")
    void constructor_upperCaseId_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> new PostInstagramConsent(
                true,
                "Chalkak",
                NOW,
                null
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("동의한 상태에서 철회하면 동의를 끄고 ID를 지우며 철회 시각만 기록한다")
    void revoke_agreedWithId_clearsAgreementAndIdAndRecordsRevokedAt() {
        // Given
        PostInstagramConsent before = PostInstagramConsent.of(
                true,
                "chalkak",
                CREATED_AT
        );

        // When
        PostInstagramConsent after = before.revoke(NOW);

        // Then
        assertThat(after.agreed()).isFalse();
        assertThat(after.instagramId()).isNull();
        assertThat(after.agreedAt()).isEqualTo(CREATED_AT);
        assertThat(after.revokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이미 동의하지 않은 상태에서 철회해도 이전 철회 시각을 포함해 아무것도 바뀌지 않는다")
    void revoke_notAgreed_keepsEverything() {
        // Given
        PostInstagramConsent neverAgreed = PostInstagramConsent.of(
                false,
                null,
                CREATED_AT
        );
        PostInstagramConsent previouslyRevoked = PostInstagramConsent
                .of(
                        true,
                        null,
                        CREATED_AT
                )
                .replace(
                        false,
                        null,
                        NOW
                );

        // When
        PostInstagramConsent neverAgreedAfter = neverAgreed.revoke(NOW.plusSeconds(60));
        PostInstagramConsent previouslyRevokedAfter = previouslyRevoked.revoke(NOW.plusSeconds(60));

        // Then
        assertThat(neverAgreedAfter).isEqualTo(neverAgreed);
        assertThat(previouslyRevokedAfter).isEqualTo(previouslyRevoked);
        assertThat(previouslyRevokedAfter.revokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("삭제로 ID만 지워진 동의 상태도 철회하면 동의를 끄고 철회 시각을 기록한다")
    void revoke_agreedWithoutIdAfterDeletion_recordsRevokedAt() {
        // Given
        PostInstagramConsent deleted = PostInstagramConsent
                .of(
                        true,
                        "chalkak",
                        CREATED_AT
                )
                .clearInstagramId();

        // When
        PostInstagramConsent after = deleted.revoke(NOW);

        // Then
        assertThat(after.agreed()).isFalse();
        assertThat(after.agreedAt()).isEqualTo(CREATED_AT);
        assertThat(after.revokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("검색어는 저장 시와 같은 규칙으로 정규화하고 비면 null을 돌려준다")
    void normalizeId_appliesStorageNormalization() {
        // When & Then
        assertThat(PostInstagramConsent.normalizeId("  @Chalkak_Official "))
                .isEqualTo("chalkak_official");
        assertThat(PostInstagramConsent.normalizeId("@@Chalkak"))
                .isEqualTo("@chalkak");
        assertThat(PostInstagramConsent.normalizeId(" @ ")).isNull();
        assertThat(PostInstagramConsent.normalizeId(null)).isNull();
    }
}
