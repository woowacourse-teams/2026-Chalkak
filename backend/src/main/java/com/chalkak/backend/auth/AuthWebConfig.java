package com.chalkak.backend.auth;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code prod}에서는 {@link LoginUserArgumentResolver}를 등록하지 않는다. 헤더 값을 그대로 신뢰하는 임시 수단이라 운영에 노출할 수 없다.
 * Resolver가 없으면 {@link LoginUser} 파라미터를 해석하지 못해 해당 API가 동작하지 않는다.
 *
 * <p>Spring Security 도입 시 이 제한을 해제한다.
 */
@Configuration
@Profile("!prod")
public class AuthWebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginUserArgumentResolver());
    }
}
