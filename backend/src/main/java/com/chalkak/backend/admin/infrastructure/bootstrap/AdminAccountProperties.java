package com.chalkak.backend.admin.infrastructure.bootstrap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chalkak.admin.account")
public record AdminAccountProperties(
        @NotBlank
        @Size(max = 100) String username,

        @NotBlank
        @Pattern(regexp = "\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")
        String passwordHash
) {
}
