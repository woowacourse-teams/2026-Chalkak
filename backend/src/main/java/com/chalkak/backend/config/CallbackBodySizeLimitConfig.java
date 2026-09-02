package com.chalkak.backend.config;

import com.chalkak.backend.auth.api.support.CallbackBodySizeLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 본문 크기 제한은 이미지 처리 콜백 경로에만 건다. 공개 API는 각자의 검증을 따르고, 이 경로만 인증 이전
 * 단계에서 자원을 쓰기 때문이다.
 */
@Configuration(proxyBeanMethods = false)
public class CallbackBodySizeLimitConfig {

    private static final String CALLBACK_PATH_PATTERN = "/internal/v1/*";

    @Bean
    public FilterRegistrationBean<CallbackBodySizeLimitFilter> callbackBodySizeLimitFilter(
            @Value("${chalkak.callback.max-body-bytes}") long maxBytes
    ) {
        FilterRegistrationBean<CallbackBodySizeLimitFilter> registration =
                new FilterRegistrationBean<>(new CallbackBodySizeLimitFilter(maxBytes));
        registration.addUrlPatterns(CALLBACK_PATH_PATTERN);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registration;
    }
}
