package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.converter.AdminTopicSortConverter;
import com.chalkak.backend.admin.service.AdminTopicDetail;
import com.chalkak.backend.admin.service.AdminTopicListResult;
import com.chalkak.backend.admin.service.AdminTopicService;
import com.chalkak.backend.admin.service.AdminTopicSort;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.topic.domain.TopicPhase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminTopicController.class)
@Import({
        GlobalExceptionHandler.class,
        AdminArgumentResolverWebMvcConfig.class,
        AdminTopicSortConverter.class
})
class AdminTopicControllerTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("0198fd21-0000-7000-8000-000000000001");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198fd21-0000-7000-8000-000000000002");
    private static final LocalDate TOPIC_DATE = LocalDate.of(2026, 8, 30);
    private static final Instant STARTS_AT = Instant.parse("2026-08-29T15:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-08-30T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminTopicService adminTopicService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("관리자는 phase와 날짜·정렬·페이지 조건으로 주제 목록을 조회한다")
    void getTopics_withFilters_returnsTopicPage() throws Exception {
        given(adminTopicService.getTopics(
                TopicPhase.BEFORE_OPEN,
                LocalDate.of(2026, 8, 29),
                LocalDate.of(2026, 8, 31),
                AdminTopicSort.TOPIC_DATE_ASC,
                2,
                10
        )).willReturn(new AdminTopicListResult(
                2,
                10,
                false,
                List.of(detail("공개 전 주제"))
        ));

        mockMvc.perform(get("/api/v1/admin/topics")
                        .queryParam("phase", "BEFORE_OPEN")
                        .queryParam("dateFrom", "2026-08-29")
                        .queryParam("dateTo", "2026-08-31")
                        .queryParam("sort", "topicDateAsc")
                        .queryParam("page", "2")
                        .queryParam("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.topics[0].topicId").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.topics[0].phase").value("BEFORE_OPEN"))
                .andExpect(jsonPath("$.topics[0].postCounts.total").value(3));
    }

    @Test
    @DisplayName("관리자는 새 주제를 등록한다")
    void createTopic_validRequest_returnsCreatedTopic() throws Exception {
        given(adminTopicService.createTopic(
                ADMIN_ID,
                "새 주제",
                TOPIC_DATE,
                STARTS_AT,
                ENDS_AT
        )).willReturn(detail("새 주제"));

        mockMvc.perform(post("/api/v1/admin/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("새 주제")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("새 주제"));
    }

    @Test
    @DisplayName("관리자는 주제 상세를 조회한다")
    void getTopic_existingTopic_returnsDetail() throws Exception {
        given(adminTopicService.getTopic(TOPIC_ID)).willReturn(detail("상세 주제"));

        mockMvc.perform(get("/api/v1/admin/topics/{topicId}", TOPIC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicId").value(TOPIC_ID.toString()))
                .andExpect(jsonPath("$.title").value("상세 주제"));
    }

    @Test
    @DisplayName("관리자는 공개 전 주제를 수정한다")
    void updateTopic_validRequest_returnsUpdatedTopic() throws Exception {
        given(adminTopicService.updateTopic(
                TOPIC_ID,
                ADMIN_ID,
                "수정 주제",
                TOPIC_DATE,
                STARTS_AT,
                ENDS_AT
        )).willReturn(detail("수정 주제"));

        mockMvc.perform(put("/api/v1/admin/topics/{topicId}", TOPIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("수정 주제")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정 주제"));
    }

    @Test
    @DisplayName("관리자는 사유와 함께 공개 전 주제를 삭제한다")
    void deleteTopic_validReason_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/topics/{topicId}", TOPIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"주제 편성 변경"}
                                """))
                .andExpect(status().isNoContent());

        then(adminTopicService).should().deleteTopic(
                TOPIC_ID,
                ADMIN_ID,
                "주제 편성 변경"
        );
    }

    @Test
    @DisplayName("빈 제목 또는 삭제 사유는 400으로 거절한다")
    void mutateTopic_blankRequiredValue_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(" ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/admin/topics/{topicId}", TOPIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":" "}
                                """))
                .andExpect(status().isBadRequest());

        then(adminTopicService).shouldHaveNoInteractions();
    }

    private AdminTopicDetail detail(String title) {
        return new AdminTopicDetail(
                TOPIC_ID,
                title,
                TOPIC_DATE,
                STARTS_AT,
                ENDS_AT,
                TopicPhase.BEFORE_OPEN,
                new AdminTopicDetail.PostCounts(3, 0, 1, 2, 0),
                Instant.parse("2026-08-28T01:00:00Z"),
                Instant.parse("2026-08-28T01:00:00Z")
        );
    }

    private String validBody(String title) {
        return """
                {
                  "title":"%s",
                  "topicDate":"2026-08-30",
                  "startsAt":"2026-08-29T15:00:00Z",
                  "endsAt":"2026-08-30T15:00:00Z"
                }
                """.formatted(title);
    }
}
