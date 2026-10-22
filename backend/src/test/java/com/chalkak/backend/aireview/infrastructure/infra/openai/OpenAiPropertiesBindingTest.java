package com.chalkak.backend.aireview.infrastructure.infra.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OpenAiPropertiesBindingTest extends IntegrationTestSupport {

    @Autowired
    private OpenAiProperties properties;

    @Test
    @DisplayName("application.yml의 허용 모델 목록이 실행 중인 컨텍스트에 바인딩된다")
    void properties_allowedModels_boundFromApplicationYml() {
        // When & Then
        assertThat(properties.allowedModels()).containsExactly("gpt-6.1-sol", "gpt-6-luna");
    }
}
