package com.chalkak.backend.aireview.infrastructure.infra.openai;

import com.chalkak.backend.aireview.service.AiReviewGenerator;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfiguration {

    @Bean("openAiHttpClient")
    public HttpClient openAiHttpClient(OpenAiProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean("openAiRestClient")
    public RestClient openAiRestClient(
            @Qualifier("openAiHttpClient") HttpClient httpClient,
            OpenAiProperties properties
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public AiReviewGenerator openAiReviewGenerator(
            @Qualifier("openAiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            Clock clock,
            OpenAiProperties properties
    ) {
        return new OpenAiResponsesReviewGenerator(
                restClient,
                objectMapper,
                clock,
                properties
        );
    }
}
