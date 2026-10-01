package com.chalkak.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.api.support.CallbackBodySizeLimitFilter;
import com.chalkak.backend.common.logging.RequestIdFilter;
import com.chalkak.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * requestId 필터가 콜백 필터와 Security 필터 체인보다 뒤로 밀리면 그 필터들이 끊은 요청이 requestId 없이 나간다.
 */
class RequestIdFilterConfigTest extends IntegrationTestSupport {

    @Autowired
    private FilterRegistrationBean<RequestIdFilter> requestIdFilter;

    @Autowired
    private FilterRegistrationBean<CallbackBodySizeLimitFilter> callbackBodySizeLimitFilter;

    @Autowired
    @Qualifier("securityFilterChainRegistration")
    private DelegatingFilterProxyRegistrationBean securityFilterChainRegistration;

    @Test
    @DisplayName("requestId 필터는 콜백 필터와 Security 필터 체인보다 먼저 실행된다")
    void requestIdFilter_order_isBeforeCallbackAndSecurityFilters() {
        // When & Then
        assertThat(requestIdFilter.getOrder())
                .isLessThan(callbackBodySizeLimitFilter.getOrder())
                .isLessThan(securityFilterChainRegistration.getOrder());
    }
}
