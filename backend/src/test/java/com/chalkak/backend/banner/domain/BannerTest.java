package com.chalkak.backend.banner.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BannerTest {

    private static final String WEB_VIEW_URL = "https://chalkak.vercel.app/event";
    private static final String THUMBNAIL_URL = "https://cdn.example.com/banner.png";

    @Test
    @DisplayName("배너 생성은 네 항목의 앞뒤 공백만 제거하고 URL 쿼리와 프래그먼트를 보존한다")
    void create_surroundingWhitespace_trimsWithoutRewritingUrl() {
        // When
        Banner banner = Banner.create(" 제목 ", " 부제목 ",
                " " + WEB_VIEW_URL + "?source=home#camera ", " " + THUMBNAIL_URL + " ");

        // Then
        assertThat(banner.getTitle()).isEqualTo("제목");
        assertThat(banner.getSubtitle()).isEqualTo("부제목");
        assertThat(banner.getWebViewUrl()).isEqualTo(WEB_VIEW_URL + "?source=home#camera");
        assertThat(banner.getThumbnailUrl()).isEqualTo(THUMBNAIL_URL);
    }

    @ParameterizedTest
    @MethodSource("emptyFields")
    @DisplayName("제목·부제목·웹뷰 URL·썸네일 URL의 null·빈 값·공백을 거절한다")
    void create_emptyField_throwsBusinessException(String field, String value) {
        // When & Then
        assertThatThrownBy(() -> createWith(field, value)).isInstanceOf(BusinessException.class);
    }

    private static Stream<Arguments> emptyFields() {
        return Stream.of("title", "subtitle", "webViewUrl", "thumbnailUrl")
                .flatMap(field -> Stream.of(null, "", " ", "\u2003")
                        .map(value -> Arguments.of(field, value)));
    }

    @ParameterizedTest
    @CsvSource({"title,1", "title,2", "title,49", "title,50", "subtitle,1", "subtitle,2",
            "subtitle,99", "subtitle,100"})
    @DisplayName("제목·부제목은 이모지도 한 글자로 세어 길이 경계까지 허용한다")
    void create_textWithinBoundary_acceptsCodePointLength(String field, int length) {
        // When
        Banner banner = createWith(field, "📷".repeat(length));

        // Then
        String stored = field.equals("title") ? banner.getTitle() : banner.getSubtitle();
        assertThat(stored.codePointCount(0, stored.length())).isEqualTo(length);
    }

    @ParameterizedTest
    @CsvSource({"title,51", "subtitle,101"})
    @DisplayName("제목·부제목의 최대 길이를 한 글자 초과하면 거절한다")
    void create_textOverBoundary_throwsBusinessException(String field, int length) {
        // When & Then
        assertThatThrownBy(() -> createWith(field, "📷".repeat(length)))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @CsvSource({"webViewUrl,1999", "webViewUrl,2000", "thumbnailUrl,1999", "thumbnailUrl,2000"})
    @DisplayName("두 URL은 앞뒤 공백 제거 후 최대 2,000자까지 허용한다")
    void create_urlWithinBoundary_acceptsNormalizedLength(String field, int length) {
        // Given
        String url = WEB_VIEW_URL + "가".repeat(length - WEB_VIEW_URL.length());

        // When
        Banner banner = createWith(field, " " + url + " ");

        // Then
        assertThat(field.equals("webViewUrl") ? banner.getWebViewUrl() : banner.getThumbnailUrl())
                .isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"webViewUrl", "thumbnailUrl"})
    @DisplayName("두 URL은 2,001자이면 거절한다")
    void create_urlOverBoundary_throwsBusinessException(String field) {
        // Given
        String url = WEB_VIEW_URL + "a".repeat(2001 - WEB_VIEW_URL.length());

        // When & Then
        assertThatThrownBy(() -> createWith(field, url)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidUrls")
    @DisplayName("두 URL의 비HTTPS·상대 주소·호스트 누락·문법 오류를 거절한다")
    void create_invalidUrl_throwsBusinessException(String field, String url) {
        // When & Then
        assertThatThrownBy(() -> createWith(field, url)).isInstanceOf(BusinessException.class);
    }

    private static Stream<Arguments> invalidUrls() {
        return Stream.of("webViewUrl", "thumbnailUrl").flatMap(field -> Stream.of(
                "http://example.com", "/event", "//example.com/event", "https:///event",
                "javascript:alert(1)", "https://", "https://exa mple.com",
                "https://example.com/%xx",
                "https://example.com:invalid/event", "https://example.com\\event")
                .map(url -> Arguments.of(field, url)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/event", "https://other.example.org/event?id=1",
            "HTTPS://EXAMPLE.COM/event", "https://example.com/안내#camera"})
    @DisplayName("Vercel 이외 HTTPS 주소도 변환 없이 허용한다")
    void create_validHttpsUrl_preservesInput(String url) {
        // When
        Banner banner = createWith("webViewUrl", url);

        // Then
        assertThat(banner.getWebViewUrl()).isEqualTo(url);
    }

    private Banner createWith(String field, String value) {
        return Banner.create(field.equals("title") ? value : "제목",
                field.equals("subtitle") ? value : "부제목",
                field.equals("webViewUrl") ? value : WEB_VIEW_URL,
                field.equals("thumbnailUrl") ? value : THUMBNAIL_URL);
    }
}
