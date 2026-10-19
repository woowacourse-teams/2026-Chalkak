package com.chalkak.backend.admin.service.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.repository.auth.AdminRepository;
import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminBannerDeletionServiceTest extends IntegrationTestSupport {

    private static final String URL = "https://chalkak.vercel.app/event";

    @Autowired
    private AdminBannerService service;
    @Autowired
    private BannerRepository repository;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = adminRepository
                .save(Admin.create("banner-delete-manager",
                        passwordEncoder.encode("test-password")))
                .getId();
    }

    @Test
    @DisplayName("삭제 사유는 정규화해 감사에만 기록하고 배너는 목록에서 제외한다")
    void deleteBanner_active_recordsAuditAndExcludesFromList() {
        // Given
        Banner banner = repository.save(banner());

        // When
        service.deleteBanner(banner.getId(), adminId, " \u2003이벤트 종료\u2003 ");
        entityManager.flush();
        entityManager.clear();

        // Then
        Banner deleted = entityManager.find(Banner.class, banner.getId());
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(service.getBanners(1, 20).banners()).isEmpty();
        assertThat(repository.existsByWebViewUrl(URL)).isFalse();
        AdminAuditLog audit = deletionAudit(banner.getId());
        assertThat(audit.getReason()).isEqualTo("이벤트 종료");
        assertThat(audit.getActorAdminId()).isEqualTo(adminId);
        assertThat(audit.getRequestId()).isNotNull();
        assertThat(audit.getBeforeState()).containsEntry("deletedAt", null)
                .containsKeys("titleSha256", "subtitleSha256").hasSize(3);
        assertThat(audit.getAfterState())
                .containsEntry("deletedAt", deleted.getDeletedAt().toString())
                .hasSize(3);
    }

    @Test
    @DisplayName("유효한 재삭제는 최초 시각·사유·감사 한 건을 유지한다")
    void deleteBanner_repeated_preservesFirstResult() {
        // Given
        Banner banner = repository.save(banner());
        service.deleteBanner(banner.getId(), adminId, "최초 사유");
        entityManager.flush();
        var first = banner.getDeletedAt();

        // When
        service.deleteBanner(banner.getId(), adminId, "새로운 사유");
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(entityManager.find(Banner.class, banner.getId()).getDeletedAt())
                .isEqualTo(first);
        assertThat(deletionAudit(banner.getId()).getReason()).isEqualTo("최초 사유");
    }

    @Test
    @DisplayName("존재하지 않는 배너는 404용 예외로 거절한다")
    void deleteBanner_missing_throwsNotFoundException() {
        // When & Then
        assertThatThrownBy(() -> service.deleteBanner(UUID.randomUUID(), adminId, "종료"))
                .isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidReasons")
    @DisplayName("잘못된 사유는 대상 존재·삭제 여부와 관계없이 거절한다")
    void deleteBanner_invalidReason_rejectsBeforeLookup(String reason) {
        // Given
        Banner banner = repository.save(banner());
        service.deleteBanner(banner.getId(), adminId, "종료");

        // When & Then
        for (UUID id : new UUID[]{banner.getId(), UUID.randomUUID()}) {
            assertThatThrownBy(() -> service.deleteBanner(id, adminId, reason))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("배너 삭제 사유는 1자 이상 500자 이하여야 합니다.");
        }
    }

    @ParameterizedTest
    @MethodSource("validReasons")
    @DisplayName("삭제 사유는 정규화 후 코드 포인트 기준 1~500자를 허용한다")
    void deleteBanner_reasonBoundary_acceptsCodePoints(String reason) {
        // Given
        Banner banner = repository.save(banner());

        // When
        service.deleteBanner(banner.getId(), adminId, " \u2003" + reason + "\u2003 ");
        entityManager.flush();

        // Then
        assertThat(deletionAudit(banner.getId()).getReason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("삭제한 URL로 새 배너를 등록하면 새 ID로 활성 목록에 나타난다")
    void createBanner_deletedUrl_registersNewBanner() {
        // Given
        Banner old = repository.save(banner());
        service.deleteBanner(old.getId(), adminId, "종료");

        // When
        AdminBannerResult created = service.createBanner(adminId, "새 제목", "새 부제목", URL,
                "https://cdn.example.com/banner.png");
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(created.id()).isNotEqualTo(old.getId());
        assertThat(service.getBanners(1, 20).banners()).extracting(AdminBannerResult::id)
                .containsExactly(created.id());
    }

    @Test
    @DisplayName("감사 민감값 검증에 걸린 사유는 삭제하지 않는다")
    void deleteBanner_sensitiveReason_rejectsWithoutDeletion() {
        // Given
        Banner banner = repository.save(banner());

        // When & Then
        assertThatThrownBy(
                () -> service.deleteBanner(banner.getId(), adminId, "token: secret-value"))
                .isInstanceOf(BusinessException.class);
    }

    private static Stream<String> invalidReasons() {
        return Stream.of(null, "", " ", "\u2003", "가".repeat(501), "😀".repeat(501));
    }

    private static Stream<String> validReasons() {
        return Stream.of("가", "가".repeat(499), "가".repeat(500), "😀".repeat(500));
    }

    private Banner banner() {
        return Banner.create("제목", "부제목", URL, "https://cdn.example.com/banner.png");
    }

    private AdminAuditLog deletionAudit(UUID id) {
        return entityManager.createQuery("""
                SELECT audit FROM AdminAuditLog audit
                WHERE audit.action = :action
                    AND audit.targetId = :id
                """, AdminAuditLog.class).setParameter("id", id)
                .setParameter("action", com.chalkak.backend.admin.domain.AdminAction.BANNER_DELETED)
                .getSingleResult();
    }
}
