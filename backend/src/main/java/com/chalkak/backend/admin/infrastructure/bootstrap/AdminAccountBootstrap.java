package com.chalkak.backend.admin.infrastructure.bootstrap;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local | dev | prod")
@ConditionalOnProperty(
        prefix = "chalkak.admin.authentication",
        name = "development-bypass-enabled",
        havingValue = "false",
        matchIfMissing = true
)
@EnableConfigurationProperties(AdminAccountProperties.class)
@RequiredArgsConstructor
public class AdminAccountBootstrap implements ApplicationRunner {

    private final AdminRepository adminRepository;
    private final AdminAccountProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (adminRepository.findByUsername(properties.username()).isPresent()) {
            return;
        }
        adminRepository.save(Admin.create(
                properties.username(),
                properties.passwordHash()
        ));
    }
}
