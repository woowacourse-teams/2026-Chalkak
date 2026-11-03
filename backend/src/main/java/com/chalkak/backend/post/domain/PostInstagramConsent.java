package com.chalkak.backend.post.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 게시물별 공식 인스타그램 게시 동의와 태그용 인스타그램 ID의 정규화·형식 규칙을 한곳에 모은 값 객체. 생성·요청 검증이 모두 이
 * 클래스에 위임해, 같은 규칙이 여러 벌로 복제되어 어긋나는 일을 막는다.
 *
 * <p>
 * 동의 여부는 항상 값이 있어 {@code posts.instagram_upload_agreed}가 {@code NOT NULL}이므로,
 * 조회 시 이 객체가 {@code null}로 복원되는 일은 없다. ID 없음은 {@code instagramId}가
 * {@code null}인 것으로 표현한다.
 */
@Embeddable
public record PostInstagramConsent(
        @Column(name = "instagram_upload_agreed", nullable = false) boolean agreed,
        @Column(name = "instagram_id", length = PostInstagramConsent.MAX_ID_LENGTH) String instagramId,
        @Column(name = "instagram_agreed_at") Instant agreedAt,
        @Column(name = "instagram_revoked_at") Instant revokedAt
) {

    private static final String INVALID_ID_FORMAT_MESSAGE = "인스타그램 ID 형식이 올바르지 않습니다.";
    private static final String ID_WITHOUT_AGREEMENT_MESSAGE = "인스타그램 게시에 동의하지 않으면 인스타그램 ID를 보낼 수 없습니다.";
    private static final int MAX_ID_LENGTH = 30;
    private static final String LEADING_AT = "@";
    /**
     * 대소문자를 모두 허용한다. 소문자 변환은 ASCII로 판정한 뒤에만 하므로 켈빈 기호(U+212A)처럼 소문자 변환 결과가 ASCII가
     * 되는 문자는 통과하지 못한다.
     */
    private static final Pattern ID_PATTERN = Pattern
            .compile("[A-Za-z0-9._]{1,%d}".formatted(MAX_ID_LENGTH));

    public PostInstagramConsent {
        if (instagramId != null) {
            validateIdAgreement(agreed);
            validateIdFormat(instagramId);
            validateIdLowerCase(instagramId);
        }
    }

    /**
     * 새 게시물의 동의 정보를 만든다. ID는 앞뒤 공백 제거, 맨 앞 {@code @} 하나 제거, 소문자 변환 순으로 정규화하고 비어 있으면
     * ID 없음으로 본다. 동의한 경우에만 동의 시각을 {@code now}로 기록한다.
     */
    public static PostInstagramConsent of(
            boolean agreed,
            String rawInstagramId,
            Instant now
    ) {
        String candidate = withoutLeadingAt(rawInstagramId);
        if (candidate.isEmpty()) {
            return new PostInstagramConsent(
                    agreed,
                    null,
                    agreed ? now : null,
                    null
            );
        }
        validateIdAgreement(agreed);
        validateIdFormat(candidate);
        return new PostInstagramConsent(
                true,
                candidate.toLowerCase(Locale.ROOT),
                now,
                null
        );
    }

    /**
     * 수정 요청으로 동의 정보를 통째로 교체한다. 동의 여부가 실제로 바뀔 때만 시각을 기록한다. 미동의에서 동의로 바뀌면 동의 시각을
     * {@code now}로 갱신하고, 동의에서 미동의로 바뀌면 철회 시각을 {@code now}로 기록한다. 동의 여부가 같으면(ID만
     * 바뀌어도) 시각을 유지한다. 이전 철회 시각은 이력으로 남기므로 현재 동의 상태는 항상 {@code agreed}로 판단한다.
     */
    public PostInstagramConsent replace(
            boolean newAgreed,
            String rawInstagramId,
            Instant now
    ) {
        PostInstagramConsent requested = of(
                newAgreed,
                rawInstagramId,
                now
        );
        if (newAgreed == agreed) {
            return new PostInstagramConsent(
                    newAgreed,
                    requested.instagramId(),
                    agreedAt,
                    revokedAt
            );
        }
        if (newAgreed) {
            return new PostInstagramConsent(
                    true,
                    requested.instagramId(),
                    now,
                    revokedAt
            );
        }
        return revoke(now);
    }

    /**
     * 게시물 삭제 시 개인정보인 인스타그램 ID만 지운다. 동의 여부와 동의·철회 시각은 이력으로 유지한다.
     */
    public PostInstagramConsent clearInstagramId() {
        return new PostInstagramConsent(
                agreed,
                null,
                agreedAt,
                revokedAt
        );
    }

    /**
     * 회원 탈퇴로 동의를 철회한다. 동의 중일 때만 동의를 끄고 ID를 지우며 철회 시각을 {@code now}로 기록한다. 동의 시각은 이력으로
     * 유지한다. 이미 동의하지 않은 상태는 이전 철회 시각까지 그대로 돌려준다.
     */
    public PostInstagramConsent revoke(Instant now) {
        if (!agreed) {
            return this;
        }
        return new PostInstagramConsent(
                false,
                null,
                agreedAt,
                now
        );
    }

    /**
     * 요청 DTO의 빠른 실패용. 예외 대신 판정만 돌려준다. 정규화 후 비어 있으면 ID 없음이므로 허용한다.
     */
    public static boolean isIdFormatValid(String raw) {
        String candidate = withoutLeadingAt(raw);
        return candidate.isEmpty() || ID_PATTERN.matcher(candidate).matches();
    }

    /**
     * 요청 DTO의 빠른 실패용. 동의하지 않은 요청은 정규화 후 ID가 비어 있을 때만 허용한다.
     */
    public static boolean isIdAllowedWithAgreement(
            boolean agreed,
            String raw
    ) {
        return agreed || withoutLeadingAt(raw).isEmpty();
    }

    /**
     * 관리자 검색어를 저장된 ID와 비교할 수 있게 정규화한다. 저장 시 정규화(앞뒤 공백 제거, 맨 앞 {@code @} 하나 제거, 소문자
     * 변환)와 같은 규칙이며, 형식 검증은 하지 않는다. 정규화 후 비어 있으면 {@code null}을 돌려준다.
     */
    public static String normalizeSearchKeyword(String raw) {
        String candidate = withoutLeadingAt(raw);
        if (candidate.isEmpty()) {
            return null;
        }
        return candidate.toLowerCase(Locale.ROOT);
    }

    private static String withoutLeadingAt(String raw) {
        if (raw == null) {
            return "";
        }
        String stripped = raw.strip();
        if (stripped.startsWith(LEADING_AT)) {
            return stripped.substring(LEADING_AT.length());
        }
        return stripped;
    }

    private static void validateIdAgreement(boolean agreed) {
        if (!agreed) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    ID_WITHOUT_AGREEMENT_MESSAGE);
        }
    }

    private static void validateIdFormat(String id) {
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    INVALID_ID_FORMAT_MESSAGE);
        }
    }

    private static void validateIdLowerCase(String id) {
        if (!id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    INVALID_ID_FORMAT_MESSAGE);
        }
    }
}
