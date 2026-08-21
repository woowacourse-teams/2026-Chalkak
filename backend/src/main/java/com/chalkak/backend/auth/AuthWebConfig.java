package com.chalkak.backend.auth;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 임시 인증 수단을 등록한다.
 *
 * <p>{@code prod}에서는 등록하지 않는다. {@link LoginUserArgumentResolver}가 헤더 값을 검증 없이 신뢰하므로 운영 환경에 노출되면 누구나 남의 계정을
 * 조작할 수 있다. Resolver가 없으면 {@link LoginUser} 파라미터를 해석하지 못해 해당 API가 동작하지 않는다.
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
