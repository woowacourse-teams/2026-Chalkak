package com.chalkak.backend.config;

import com.chalkak.backend.auth.api.support.AccessLogUserInterceptor;
import com.chalkak.backend.auth.api.support.LoginUserArgumentResolver;
import com.chalkak.backend.auth.api.support.OptionalLoginUserArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final OptionalLoginUserArgumentResolver optionalLoginUserArgumentResolver;
    private final AccessLogUserInterceptor accessLogUserInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
        resolvers.add(optionalLoginUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessLogUserInterceptor);
    }
}
