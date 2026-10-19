package com.chalkak.backend.banner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.exception.BusinessException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

class BannerRepositoryImplTest {

    @Test
    @DisplayName("웹뷰 URL의 고유 인덱스 위반만 중복 배너 업무 예외로 변환한다")
    void save_duplicateUrl_translatesBusinessException() {
        // Given
        BannerJpaRepository jpaRepository = mock(BannerJpaRepository.class);
        Banner banner = banner();
        ServerErrorMessage error = mock(ServerErrorMessage.class);
        given(error.getConstraint()).willReturn("ux_banners_web_view_url_digest");
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "duplicate", new PSQLException(error));
        given(jpaRepository.save(banner)).willThrow(violation);

        // When & Then
        assertThatThrownBy(() -> new BannerRepositoryImpl(jpaRepository).save(banner))
                .isInstanceOf(BusinessException.class)
                .hasMessage("동일한 웹뷰 URL의 배너가 이미 존재합니다.");
    }

    @ParameterizedTest
    @MethodSource("unrelatedErrors")
    @DisplayName("다른 제약이나 PostgreSQL 상세 정보가 없는 오류는 원래 예외를 유지한다")
    void save_unrelatedViolation_preservesException(DataIntegrityViolationException error) {
        // Given
        BannerJpaRepository jpaRepository = mock(BannerJpaRepository.class);
        Banner banner = banner();
        given(jpaRepository.save(banner)).willThrow(error);

        // When & Then
        assertThatThrownBy(() -> new BannerRepositoryImpl(jpaRepository).save(banner))
                .isSameAs(error);
    }

    private static Stream<DataIntegrityViolationException> unrelatedErrors() {
        ServerErrorMessage otherConstraint = mock(ServerErrorMessage.class);
        given(otherConstraint.getConstraint()).willReturn("ck_banners_title_not_blank");
        return Stream.of(new DataIntegrityViolationException("other"),
                new DataIntegrityViolationException("other", new PSQLException(otherConstraint)),
                new DataIntegrityViolationException("other", new PSQLException("error", null)));
    }

    private Banner banner() {
        return Banner.create("제목", "부제목", "https://chalkak.vercel.app/event",
                "https://cdn.example.com/banner.png");
    }
}
