package com.chalkak.backend.config;

import com.chalkak.backend.auth.api.support.OptionalLoginUserArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class OptionalLoginUserWebMvcConfig implements WebMvcConfigurer {

    private final OptionalLoginUserArgumentResolver optionalLoginUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(optionalLoginUserArgumentResolver);
    }
}
