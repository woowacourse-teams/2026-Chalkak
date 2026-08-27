package com.chalkak.backend.admin.api.support;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
@RequiredArgsConstructor
public class ProdAdminAccessWebMvcConfig implements WebMvcConfigurer {

    private static final String ADMIN_API_PATH = "/api/v1/admin";
    private static final String ADMIN_API_PATH_PATTERN = ADMIN_API_PATH + "/**";

    private final ProdAdminAccessInterceptor prodAdminAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(prodAdminAccessInterceptor)
                .addPathPatterns(ADMIN_API_PATH, ADMIN_API_PATH_PATTERN)
                .order(Ordered.HIGHEST_PRECEDENCE);
    }
}
