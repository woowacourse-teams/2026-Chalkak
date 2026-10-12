package com.chalkak.backend.analytics.infrastructure.infra;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnalyticsProperties.class)
public class AnalyticsConfig {
}
