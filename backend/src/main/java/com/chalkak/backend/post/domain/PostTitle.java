package com.chalkak.backend.post.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * 게시물 제목의 정규화·길이 규칙을 한곳에 모은 값 객체. 생성·수정·요청 검증이 모두 이 클래스에 위임해, 같은 규칙이
 * 여러 벌로 복제되어 어긋나는 일을 막는다.
 *
 * <p>제목 없음은 {@code PostTitle} 자체가 {@code null}인 것으로만 표현한다. 값이 {@code null}인 인스턴스를
 * 허용하면 {@code posts.title}이 컬럼 하나뿐이라 두 상태를 구분해 저장할 수 없고, 조회 시 Hibernate가 임베디드
 * 필드를 {@code null}로 복원해 저장 직후 객체와 조회한 객체의 모양이 달라진다.
 */
@Embeddable
public record PostTitle(
        @Column(name = "title", length = PostTitle.MAX_LENGTH)
        String value
) {

    private static final int MAX_LENGTH = 10;

    public PostTitle {
        Objects.requireNonNull(value, "PostTitle은 값 없이 만들 수 없다. 제목 없음은 null로 표현한다.");
    }

    /**
     * 앞뒤 공백을 제거하고 가운데 공백은 유지한다. 제거 후 빈 문자열이면 제목 없음이므로 {@code null}을 돌려준다.
     */
    public static PostTitle from(String raw) {
        if (raw == null) {
            return null;
        }
        String stripped = raw.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        if (exceedsMaxLength(stripped)) {
            throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "제목은 %d자 이하여야 합니다.".formatted(MAX_LENGTH)
            );
        }
        return new PostTitle(stripped);
    }

    /**
     * 요청 DTO의 빠른 실패용. 예외 대신 판정만 돌려준다. 공백뿐인 제목은 제목 없음으로 정규화되므로 길이를 재지 않는다.
     */
    public static boolean isValid(String raw) {
        return raw == null || !exceedsMaxLength(raw.strip());
    }

    /**
     * 이모지 한 글자는 UTF-16 code unit 두 칸을 쓰므로 {@code String.length()}로 세면 사용자가 입력한 글자 수보다 길게
     * 계산된다. {@code posts.title}이 code point를 세는 {@code VARCHAR(10)}이므로 길이도 code point로 판정한다.
     */
    private static boolean exceedsMaxLength(String value) {
        return value.codePointCount(0, value.length()) > MAX_LENGTH;
    }
}
