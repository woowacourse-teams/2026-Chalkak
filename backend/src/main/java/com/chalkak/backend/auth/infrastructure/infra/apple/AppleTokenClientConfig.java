package com.chalkak.backend.auth.infrastructure.infra.apple;

import com.chalkak.backend.auth.infrastructure.infra.oidc.apple.AppleOidcProperties;
import com.chalkak.backend.auth.service.AppleTokenClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AppleTokenClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean("appleTokenHttpClient")
    public HttpClient appleTokenHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean("appleTokenRestClient")
    public RestClient appleTokenRestClient(
            @Qualifier("appleTokenHttpClient") HttpClient httpClient
    ) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public AppleTokenClient appleTokenClient(
            @Qualifier("appleTokenRestClient") RestClient restClient,
            AppleTokenProperties tokenProperties,
            AppleOidcProperties oidcProperties,
            AppleClientSecretGenerator clientSecretGenerator
    ) {
        return new AppleHttpTokenClient(
                restClient,
                tokenProperties,
                oidcProperties,
                clientSecretGenerator);
    }
}
