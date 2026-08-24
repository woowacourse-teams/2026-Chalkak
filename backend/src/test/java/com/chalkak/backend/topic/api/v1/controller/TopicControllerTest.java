package com.chalkak.backend.topic.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.topic.domain.TopicPhase;
import com.chalkak.backend.topic.service.TopicDetail;
import com.chalkak.backend.topic.service.TopicQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TopicController.class)
@Import(GlobalExceptionHandler.class)
class TopicControllerTest {

    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final LocalDate TOPIC_DATE = LocalDate.of(2026, 8, 12);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicQueryService topicQueryService;

    @Test
    @DisplayName("공개일로 주제를 조회한다")
    void getTopic_openedTopic_returnsTopicDetail() throws Exception {
        // Given
        TopicDetail detail = new TopicDetail(
                TOPIC_ID,
                "오늘 가장 기억에 남은 순간",
                TOPIC_DATE,
                Instant.parse("2026-08-11T15:00:00Z"),
                Instant.parse("2026-08-12T15:00:00Z"),
                TopicPhase.OPEN
        );
        given(topicQueryService.getTopic(TOPIC_DATE)).willReturn(detail);

        // When & Then
        mockMvc.perform(get("/api/v1/topics").param("date", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.title").value("오늘 가장 기억에 남은 순간"))
                .andExpect(jsonPath("$.topicDate").value("2026-08-12"))
                .andExpect(jsonPath("$.startsAt").value("2026-08-12T00:00:00+09:00"))
                .andExpect(jsonPath("$.endsAt").value("2026-08-13T00:00:00+09:00"))
                .andExpect(jsonPath("$.phase").value("OPEN"));
    }

    @Test
    @DisplayName("날짜 형식이 올바르지 않으면 400을 반환한다")
    void getTopic_invalidDateFormat_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/topics").param("date", "2026-13-99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("date: 요청 값의 형식이 올바르지 않습니다."));
    }
}
