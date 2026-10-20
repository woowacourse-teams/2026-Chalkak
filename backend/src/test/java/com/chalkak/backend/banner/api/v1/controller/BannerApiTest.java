package com.chalkak.backend.banner.api.v1.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
class BannerApiTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BannerRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("익명 조회는 삭제 배너를 제외하고 최신순·ID 내림차순으로 페이지를 구성한다")
    void getBanners_anonymous_returnsOnlyActiveBannersInOrder() throws Exception {
        // Given
        Banner older = save("old");
        Banner first = save("first");
        Banner second = save("second");
        Banner deleted = save("deleted");
        deleted.delete(Instant.now());
        entityManager.flush();
        jdbcTemplate.update("UPDATE banners SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse("2026-10-18T00:00:00Z")), older.getId());
        jdbcTemplate.update("UPDATE banners SET created_at = ? WHERE id IN (?, ?)",
                java.sql.Timestamp.from(Instant.parse("2026-10-19T00:00:00Z")), first.getId(),
                second.getId());
        entityManager.clear();
        List<String> newest = List.of(first.getId().toString(), second.getId().toString()).stream()
                .sorted(Comparator.reverseOrder()).toList();

        // When & Then
        mockMvc.perform(get("/api/v1/banners").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banners.length()").value(2))
                .andExpect(jsonPath("$.banners[0].id").value(newest.get(0)))
                .andExpect(jsonPath("$.banners[1].id").value(newest.get(1)))
                .andExpect(jsonPath("$.hasNext").value(true));
        mockMvc.perform(get("/api/v1/banners").param("page", "2").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banners.length()").value(1))
                .andExpect(jsonPath("$.banners[0].id").value(older.getId().toString()))
                .andExpect(jsonPath("$.hasNext").value(false));
        mockMvc.perform(get("/api/v1/banners").param("page", "3").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banners").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("등록된 배너가 없으면 기본 페이지와 빈 목록을 반환한다")
    void getBanners_empty_returnsDefaultPage() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.banners").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @ParameterizedTest
    @CsvSource({"0, 20", "1, 0", "1, 101", "21474838, 100"})
    @DisplayName("잘못된 페이지·크기와 offset 초과는 실제 서비스에서 400으로 거절한다")
    void getBanners_invalidPagination_returnsBadRequest(int page, int size) throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/banners").param("page", String.valueOf(page))
                .param("pageSize", String.valueOf(size)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    private Banner save(String title) {
        return repository.save(Banner.create(title, "부제목", "https://example.com/" + title,
                "https://example.com/image.png"));
    }
}
