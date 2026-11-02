package com.chalkak.backend.banner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.banner.repository.BannerPage;
import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {

    @Mock
    private BannerRepository repository;

    @InjectMocks
    private BannerService service;

    @Test
    @DisplayName("조회된 배너의 필드와 페이지 정보를 그대로 반환한다")
    void getBanners_existingPage_returnsFieldsAndMetadata() {
        // Given
        UUID id = UUID.fromString("0198fc00-0000-7000-8000-000000000001");
        Instant createdAt = Instant.parse("2026-10-18T00:00:00Z");
        Banner banner = Banner.create(
                "제목",
                "부제목",
                "https://example.com/event",
                "https://example.com/thumbnail.png"
        );
        ReflectionTestUtils.setField(
                banner,
                "id",
                id
        );
        ReflectionTestUtils.setField(
                banner,
                "createdAt",
                createdAt
        );
        given(repository.findBanners(2, 1)).willReturn(new BannerPage(
                List.of(banner),
                2,
                1,
                true
        ));

        // When
        BannerListResult result = service.getBanners(2, 1);

        // Then
        assertThat(result.currentPage()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(1);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.banners()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(id);
            assertThat(item.title()).isEqualTo("제목");
            assertThat(item.subtitle()).isEqualTo("부제목");
            assertThat(item.webViewUrl()).isEqualTo("https://example.com/event");
            assertThat(item.thumbnailUrl()).isEqualTo("https://example.com/thumbnail.png");
            assertThat(item.createdAt()).isEqualTo(createdAt);
        });
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "1, 20", "1, 99", "1, 100", "2147483647, 1", "21474837, 100"})
    @DisplayName("허용된 페이지와 offset 경계에서는 빈 조회 결과의 메타데이터를 반환한다")
    void getBanners_validPagination_returnsEmptyPage(int page, int pageSize) {
        // Given
        given(repository.findBanners(page, pageSize)).willReturn(new BannerPage(
                List.of(),
                page,
                pageSize,
                false
        ));

        // When
        BannerListResult result = service.getBanners(page, pageSize);

        // Then
        assertThat(result.currentPage()).isEqualTo(page);
        assertThat(result.pageSize()).isEqualTo(pageSize);
        assertThat(result.banners()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0, 20", "-1, 20", "1, 0", "1, -1", "1, 101", "21474838, 100", "2147483647, 100"})
    @DisplayName("잘못된 페이지와 크기 또는 offset 초과는 DB를 조회하지 않고 거절한다")
    void getBanners_invalidPagination_throwsBusinessException(int page, int pageSize) {
        // When & Then
        assertThatThrownBy(() -> service.getBanners(page, pageSize))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 조건이 올바르지 않습니다.");
        then(repository).shouldHaveNoInteractions();
    }
}
