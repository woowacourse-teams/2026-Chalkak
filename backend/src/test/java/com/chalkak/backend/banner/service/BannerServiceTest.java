package com.chalkak.backend.banner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.banner.repository.BannerPage;
import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {

    @Mock
    private BannerRepository repository;

    @InjectMocks
    private BannerService service;

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
