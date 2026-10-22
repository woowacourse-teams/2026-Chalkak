package com.chalkak.backend.banner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class BannerRepositoryTest extends IntegrationTestSupport {

    private static final String URL = "https://chalkak.vercel.app/event?source=home#camera";

    @Autowired
    private BannerRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("URL 사전 조회는 전체 문자열을 비교하고 쿼리·프래그먼트 차이를 구분한다")
    void existsByWebViewUrl_completeUrl_matchesExactly() {
        // Given
        assertThat(repository.existsByWebViewUrl(URL)).isFalse();
        repository.save(banner(URL));
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(repository.existsByWebViewUrl(URL)).isTrue();
        assertThat(repository.existsByWebViewUrl(URL.replace("home", "notice"))).isFalse();
        assertThat(repository.existsByWebViewUrl(URL.replace("#camera", "#profile"))).isFalse();
        assertThat(repository.existsByWebViewUrl("https://chalkak.vercel.app/event")).isFalse();
    }

    @Test
    @DisplayName("save만 호출해도 DB 생성 UUID와 생성 시각을 즉시 받는다")
    void save_dbGeneratedValues_returnsBeforeExplicitFlush() {
        // When
        Banner saved = repository.save(banner(URL));

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId().version()).isEqualTo(7);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("사전 검사를 우회한 중복도 save 안에서 업무 예외로 변환한다")
    void save_duplicateWithoutPrecheck_translatesBeforeExplicitFlush() {
        // Given
        repository.save(banner(URL));

        // When & Then
        assertThatThrownBy(() -> repository.save(banner(URL)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("동일한 웹뷰 URL의 배너가 이미 존재합니다.");
    }

    @Test
    @DisplayName("부분 고유 인덱스는 삭제된 URL 재등록을 허용하고 활성 중복은 거절한다")
    void save_deletedUrl_allowsReplacementButRejectsActiveDuplicate() {
        // Given
        Banner old = repository.save(banner(URL));
        old.delete(java.time.Instant.now());
        entityManager.flush();
        entityManager.clear();
        assertThat(repository.existsByWebViewUrl(URL)).isFalse();

        // When
        Banner replacement = repository.save(banner(URL));
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(repository.existsByWebViewUrl(URL)).isTrue();
        assertThat(repository.findBanners(1, 1).items()).extracting(Banner::getId)
                .containsExactly(replacement.getId());
        assertThat(repository.findBanners(1, 1).hasNext()).isFalse();
        assertThatThrownBy(() -> repository.save(banner(URL)))
                .isInstanceOf(BusinessException.class);
    }

    private Banner banner(String url) {
        return Banner.create(
                "제목",
                "부제목",
                url,
                "https://cdn.example.com/banner.png"
        );
    }
}
