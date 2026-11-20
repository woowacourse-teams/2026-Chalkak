package com.chalkak.backend.jev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.jev.repository.JevPromptVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JevAutomationServiceTest {

    @Test
    @DisplayName("전송이 꺼졌으면 프롬프트를 읽거나 호출을 예약하지 않고 수동 등록 설정을 반환한다")
    void selectActive_workerDisabled_returnsManualWithoutPromptLookup() {
        // Given
        JevPromptVersionRepository prompts = mock(JevPromptVersionRepository.class);
        JevAutomationService automation = new JevAutomationService(prompts, false);
        // When
        var selected = automation.selectActive();
        // Then
        assertThat(selected.manualReason()).isEqualTo("AUTOMATION_DISABLED");
        assertThat(selected.classification()).isNull();
        assertThat(selected.photo()).isNull();
        verifyNoInteractions(prompts);
    }
}
