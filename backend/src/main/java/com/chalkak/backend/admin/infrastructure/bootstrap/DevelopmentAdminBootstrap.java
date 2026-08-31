package com.chalkak.backend.admin.infrastructure.bootstrap;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!dev & !prod & (local | test)")
@ConditionalOnProperty(
        prefix = "chalkak.admin.authentication",
        name = "development-bypass-enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class DevelopmentAdminBootstrap implements ApplicationRunner {

    public static final String DEVELOPMENT_ADMIN_USERNAME = "dev-admin";

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final AdminRepository adminRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (adminRepository.findByUsername(DEVELOPMENT_ADMIN_USERNAME).isPresent()) {
            return;
        }

        String unavailablePassword = UUID.randomUUID().toString();
        String passwordHash = PASSWORD_ENCODER.encode(unavailablePassword);
        adminRepository.save(Admin.create(DEVELOPMENT_ADMIN_USERNAME, passwordHash));
    }
}
