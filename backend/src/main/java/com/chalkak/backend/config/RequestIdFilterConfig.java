package com.chalkak.backend.config;

import com.chalkak.backend.common.logging.RequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 모든 요청에 requestId를 붙인다. 다른 서블릿 필터와 Security 필터 체인보다 앞에 있어야 거절된 요청도 추적할 수 있다.
 */
@Configuration(proxyBeanMethods = false)
public class RequestIdFilterConfig {

    private static final String ALL_PATH_PATTERN = "/*";

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration =
                new FilterRegistrationBean<>(new RequestIdFilter());
        registration.addUrlPatterns(ALL_PATH_PATTERN);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registration;
    }
}
