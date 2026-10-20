package com.chalkak.backend.admin.service.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.repository.auth.AdminRepository;
import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.banner.service.BannerResult;
import com.chalkak.backend.banner.service.BannerService;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminBannerDeletionServiceTest extends IntegrationTestSupport {

    private static final String URL = "https://chalkak.vercel.app/event";

    @Autowired
    private AdminBannerService service;
    @Autowired
    private BannerService bannerService;
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
    @DisplayName("사유 없이 삭제 감사를 기록하고 배너는 목록에서 제외한다")
    void deleteBanner_active_recordsAuditAndExcludesFromList() {
        // Given
        Banner banner = repository.save(banner());

        // When
        service.deleteBanner(banner.getId(), adminId);
        entityManager.flush();
        entityManager.clear();

        // Then
        Banner deleted = entityManager.find(Banner.class, banner.getId());
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(bannerService.getBanners(1, 20).banners()).isEmpty();
        assertThat(repository.existsByWebViewUrl(URL)).isFalse();
        AdminAuditLog audit = deletionAudit(banner.getId());
        assertThat(audit.getReason()).isNull();
        assertThat(audit.getActorAdminId()).isEqualTo(adminId);
        assertThat(audit.getRequestId()).isNotNull();
        assertThat(audit.getBeforeState()).containsEntry("deletedAt", null)
                .containsKeys("titleSha256", "subtitleSha256").hasSize(3);
        assertThat(audit.getAfterState())
                .containsEntry("deletedAt", deleted.getDeletedAt().toString())
                .hasSize(3);
    }

    @Test
    @DisplayName("재삭제는 최초 시각과 감사 한 건을 유지한다")
    void deleteBanner_repeated_preservesFirstResult() {
        // Given
        Banner banner = repository.save(banner());
        service.deleteBanner(banner.getId(), adminId);
        entityManager.flush();
        var first = banner.getDeletedAt();

        // When
        service.deleteBanner(banner.getId(), adminId);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(entityManager.find(Banner.class, banner.getId()).getDeletedAt())
                .isEqualTo(first);
        assertThat(deletionAudit(banner.getId()).getReason()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 배너는 404용 예외로 거절한다")
    void deleteBanner_missing_throwsNotFoundException() {
        // When & Then
        assertThatThrownBy(() -> service.deleteBanner(UUID.randomUUID(), adminId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("삭제한 URL로 새 배너를 등록하면 새 ID로 활성 목록에 나타난다")
    void createBanner_deletedUrl_registersNewBanner() {
        // Given
        Banner old = repository.save(banner());
        service.deleteBanner(old.getId(), adminId);

        // When
        BannerResult created = service.createBanner(adminId, "새 제목", "새 부제목", URL,
                "https://cdn.example.com/banner.png");
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(created.id()).isNotEqualTo(old.getId());
        assertThat(bannerService.getBanners(1, 20).banners()).extracting(BannerResult::id)
                .containsExactly(created.id());
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
