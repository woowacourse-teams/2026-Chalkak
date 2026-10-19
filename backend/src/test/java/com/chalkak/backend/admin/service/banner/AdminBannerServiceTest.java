package com.chalkak.backend.admin.service.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminBannerServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000676");
    private static final String URL = "https://chalkak.vercel.app/event";

    @Autowired
    private AdminBannerService bannerService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BannerRepository bannerRepository;

    private final UUID requestId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MDC.put(LogFields.REQUEST_ID, requestId.toString());
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, 'banner-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(LogFields.REQUEST_ID);
    }

    @Test
    @DisplayName("배너 등록은 정규화한 정보를 저장하고 URL 없이 감사 로그를 남긴다")
    void createBanner_validInput_persistsBannerAndAudit() throws Exception {
        // When
        AdminBannerResult result = bannerService.createBanner(ADMIN_ID, " 제목 ", " 부제목 ",
                " " + URL + " ", " https://cdn.example.com/banner.png ");
        entityManager.flush();
        entityManager.clear();

        // Then
        Banner stored = entityManager.find(Banner.class, result.id());
        assertThat(stored.getTitle()).isEqualTo("제목");
        assertThat(stored.getSubtitle()).isEqualTo("부제목");
        assertThat(stored.getWebViewUrl()).isEqualTo(URL);
        assertThat(stored.getThumbnailUrl()).isEqualTo("https://cdn.example.com/banner.png");
        assertThat(result.createdAt()).isEqualTo(stored.getCreatedAt());
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(result.id().version()).isEqualTo(7);
        AdminAuditLog audit = entityManager.createQuery(
                "SELECT a FROM AdminAuditLog a WHERE a.targetId = :id", AdminAuditLog.class)
                .setParameter("id", result.id()).getSingleResult();
        assertThat(audit.getActorAdminId()).isEqualTo(ADMIN_ID);
        assertThat(audit.getAction().name()).isEqualTo("BANNER_CREATED");
        assertThat(audit.getTargetType().name()).isEqualTo("BANNER");
        assertThat(audit.getRequestId()).isEqualTo(requestId);
        assertThat(audit.getReason()).isNull();
        assertThat(audit.getBeforeState()).isEmpty();
        assertThat(audit.getAfterState()).containsOnlyKeys("titleSha256", "subtitleSha256")
                .containsEntry("titleSha256", sha256("제목"))
                .containsEntry("subtitleSha256", sha256("부제목"));
    }

    @Test
    @DisplayName("관리자 ID가 없으면 배너를 저장하지 않는다")
    void createBanner_nullAdmin_throwsBeforeSaving() {
        // When & Then
        assertThatThrownBy(() -> bannerService.createBanner(null, "제목", "부제목", URL,
                "https://cdn.example.com/banner.png")).isInstanceOf(BusinessException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banners", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("잘못된 도메인 입력이면 배너와 감사 기록을 저장하지 않는다")
    void createBanner_invalidInput_throwsBeforeSaving() {
        // When & Then
        assertThatThrownBy(() -> bannerService.createBanner(ADMIN_ID, "제목", "부제목",
                "http://example.com", "https://cdn.example.com/banner.png"))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banners", Integer.class))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"?source=home", "?source=notice", "#camera"})
    @DisplayName("쿼리나 프래그먼트가 다르면 별도 배너를 등록한다")
    void createBanner_differentCompleteUrl_persistsBoth(String suffix) {
        // Given
        bannerService.createBanner(ADMIN_ID, "제목", "부제목", URL,
                "https://cdn.example.com/banner.png");

        // When
        bannerService.createBanner(ADMIN_ID, "제목", "부제목", URL + suffix,
                "https://cdn.example.com/banner.png");
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banners", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("중복은 사전 검사에서 거절해 DB 제약 오류 없이 기존 데이터를 유지한다")
    void createBanner_existingUrl_rejectsBeforeDatabaseViolation() {
        // Given
        bannerService.createBanner(ADMIN_ID, "제목", "부제목", URL,
                "https://cdn.example.com/banner.png");

        // When & Then
        assertThatThrownBy(() -> bannerService.createBanner(ADMIN_ID, "다른 제목", "다른 부제목",
                " " + URL + " ", "https://cdn.example.com/other.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("동일한 웹뷰 URL의 배너가 이미 존재합니다.");
        // DB 제약 오류가 발생했다면 PostgreSQL 트랜잭션이 중단돼 이 조회도 실패한다.
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banners", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("2,000자의 다국어 URL도 인덱스 크기 오류 없이 저장한다")
    void createBanner_longUnicodeUrl_persistsWithinLimit() {
        // Given
        StringBuilder url = new StringBuilder(URL);
        while (url.length() < 2000) {
            url.append((char) (0x4E00 + url.length()));
        }

        // When
        AdminBannerResult result = bannerService.createBanner(ADMIN_ID, "제목", "부제목",
                url.toString(), "https://cdn.example.com/banner.png");
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(entityManager.find(Banner.class, result.id()).getWebViewUrl())
                .isEqualTo(url.toString());
        assertThat(bannerRepository.existsByWebViewUrl(url.toString())).isTrue();
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
