package com.chalkak.backend.admin.api.support;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminCorsProperties.class)
@RequiredArgsConstructor
public class AdminCorsWebMvcConfig implements WebMvcConfigurer {

    private static final String ADMIN_API_PATH = "/api/v1/admin";
    private static final String ADMIN_API_PATH_PATTERN = ADMIN_API_PATH + "/**";

    private final AdminCorsProperties adminCorsProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        configureCors(registry.addMapping(ADMIN_API_PATH));
        configureCors(registry.addMapping(ADMIN_API_PATH_PATTERN));
    }

    private void configureCors(CorsRegistration registration) {
        registration
                .allowedOrigins(adminCorsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name())
                .allowedHeaders(
                        HttpHeaders.AUTHORIZATION,
                        HttpHeaders.CONTENT_TYPE,
                        HttpHeaders.ACCEPT)
                .allowCredentials(false)
                .maxAge(3_600);
    }
}
