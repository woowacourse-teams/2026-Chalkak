package com.chalkak.backend.admin.banner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.banner.domain.Banner;
import com.chalkak.backend.admin.banner.repository.BannerRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminBannerListServiceTest extends IntegrationTestSupport {

    @Autowired
    private AdminBannerService bannerService;

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("목록은 생성 시각과 ID 내림차순이며 페이지별 다음 페이지 여부를 반환한다")
    void getBanners_multiplePages_returnsStableOrderAndMetadata() {
        // Given
        Banner older = saveBanner("older");
        Banner newerFirst = saveBanner("newer-first");
        Banner newerSecond = saveBanner("newer-second");
        Instant oldTime = Instant.parse("2026-10-18T00:00:00Z");
        Instant newTime = oldTime.plusSeconds(60);
        jdbcTemplate.update("UPDATE banners SET created_at = ? WHERE id = ?",
                Timestamp.from(oldTime), older.getId());
        jdbcTemplate.update("UPDATE banners SET created_at = ? WHERE id IN (?, ?)",
                Timestamp.from(newTime), newerFirst.getId(), newerSecond.getId());
        entityManager.flush();
        entityManager.clear();
        List<UUID> expectedNewer = List.of(newerFirst.getId(), newerSecond.getId()).stream()
                .sorted(Comparator.comparing(UUID::toString).reversed()).toList();

        // When
        AdminBannerListResult first = bannerService.getBanners(1, 2);
        AdminBannerListResult last = bannerService.getBanners(2, 2);
        AdminBannerListResult beyond = bannerService.getBanners(3, 2);
        AdminBannerListResult exactSize = bannerService.getBanners(1, 3);

        // Then
        assertThat(exactSize.banners()).hasSize(3);
        assertThat(exactSize.hasNext()).isFalse();
        assertThat(first.currentPage()).isEqualTo(1);
        assertThat(first.pageSize()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.banners()).extracting(AdminBannerResult::id)
                .containsExactlyElementsOf(expectedNewer);
        assertThat(first.banners()).allSatisfy(banner -> {
            assertThat(banner.createdAt()).isEqualTo(newTime);
            assertThat(banner.title()).isNotBlank();
            assertThat(banner.subtitle()).isEqualTo("부제목");
            assertThat(banner.webViewUrl()).startsWith("https://example.com/");
            assertThat(banner.thumbnailUrl()).isEqualTo("https://example.com/thumbnail.png");
        });
        assertThat(last.currentPage()).isEqualTo(2);
        assertThat(last.hasNext()).isFalse();
        assertThat(last.banners()).extracting(AdminBannerResult::id).containsExactly(older.getId());
        assertThat(beyond.currentPage()).isEqualTo(3);
        assertThat(beyond.banners()).isEmpty();
        assertThat(beyond.hasNext()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banners", Integer.class))
                .isEqualTo(3);
        assertThat(
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_audit_logs", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("등록된 배너가 없으면 빈 목록과 hasNext false를 반환한다")
    void getBanners_noBanners_returnsEmptyPage() {
        // Given
        entityManager.flush();
        entityManager.clear();

        // When
        AdminBannerListResult result = bannerService.getBanners(1, 20);

        // Then
        assertThat(result.banners()).isEmpty();
        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"1, 1", "1, 99", "1, 100", "2147483647, 1", "21474837, 100"})
    @DisplayName("허용된 페이지 크기와 offset 경계에서는 조회할 수 있다")
    void getBanners_validPagination_returnsPage(int page, int pageSize) {
        // Given
        entityManager.flush();
        entityManager.clear();

        // When
        AdminBannerListResult result = bannerService.getBanners(page, pageSize);

        // Then
        assertThat(result.currentPage()).isEqualTo(page);
        assertThat(result.pageSize()).isEqualTo(pageSize);
        assertThat(result.banners()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0, 20", "-1, 20", "1, 0", "1, -1", "1, 101", "21474838, 100",
            "2147483647, 100"})
    @DisplayName("잘못된 페이지·크기 또는 offset 초과는 비즈니스 예외로 거절한다")
    void getBanners_invalidPagination_throwsBusinessException(int page, int pageSize) {
        // When & Then
        assertThatThrownBy(() -> bannerService.getBanners(page, pageSize))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 조건이 올바르지 않습니다.");
    }

    private Banner saveBanner(String name) {
        return bannerRepository.save(Banner.create(name, "부제목", "https://example.com/" + name,
                "https://example.com/thumbnail.png"));
    }
}
