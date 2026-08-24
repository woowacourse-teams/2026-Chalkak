package com.chalkak.backend.config;

import com.chalkak.backend.auth.api.support.LoginUserArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code prod}에서는 임시 인증 수단인 {@link LoginUserArgumentResolver}를 등록하지 않는다. 리졸버를 필수 생성자
 * 파라미터로 받아, 비운영 프로파일에서 리졸버가 사라지면 기동 단계에서 드러나게 한다.
 */
@Configuration
@RequiredArgsConstructor
@Profile("!prod")
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}
