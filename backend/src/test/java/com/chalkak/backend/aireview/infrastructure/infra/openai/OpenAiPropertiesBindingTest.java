package com.chalkak.backend.aireview.infrastructure.infra.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OpenAiPropertiesBindingTest {

    @Test
    @DisplayName("application.yml의 허용 모델 목록이 설정 객체에 바인딩된다")
    void properties_allowedModels_boundFromApplicationYml() {
        // Given
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("spring.profiles.active=test");

        // When & Then
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OpenAiProperties.class).allowedModels())
                    .containsExactly("gpt-6.1-sol", "gpt-6-luna");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OpenAiProperties.class)
    static class PropertiesConfiguration {
    }
}
