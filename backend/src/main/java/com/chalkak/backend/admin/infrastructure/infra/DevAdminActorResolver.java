package com.chalkak.backend.admin.infrastructure.infra;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.infrastructure.bootstrap.DevelopmentAdminBootstrap;
import com.chalkak.backend.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod & (local | dev | test)")
@ConditionalOnProperty(
        prefix = "chalkak.admin.authentication",
        name = "development-bypass-enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class DevAdminActorResolver implements AdminActorResolver {

    private final AdminRepository adminRepository;

    @Override
    public AuthenticatedAdmin resolve() {
        Admin developmentAdmin = adminRepository.findByUsername(
                DevelopmentAdminBootstrap.DEVELOPMENT_ADMIN_USERNAME
        ).orElseThrow(() -> new IllegalStateException("개발 관리자 계정이 존재하지 않습니다."));

        return new AuthenticatedAdmin(developmentAdmin.getId());
    }
}
