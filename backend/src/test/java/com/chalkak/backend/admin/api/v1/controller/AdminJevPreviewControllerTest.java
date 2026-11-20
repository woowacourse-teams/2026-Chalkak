package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.api.v1.dto.request.JevPreviewCreateRequest;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevPromptDraft;
import com.chalkak.backend.jev.domain.JevPromptFixtures;
import com.chalkak.backend.jev.domain.JevTopicCategory;
import com.chalkak.backend.jev.service.JevJobService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AdminJevPreviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminJevPreviewControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @MockitoBean
    private JevJobService service;
    @MockitoBean
    private AdminActorResolver actorResolver;

    @Test
    @DisplayName("유효한 시험 요청을 201로 등록하고 미완료 작업 식별자를 반환한다")
    void createPreview_validDraft_returnsCreatedJob() throws Exception {
        // Given
        UUID admin = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-20T00:00:00Z");
        JevPromptDraft draft = new JevPromptDraft(
                "jev-1.14.0",
                JevPromptFixtures.classification(),
                JevPromptFixtures.photo()
        );
        JevTopicCategory category = JevTopicCategory.create(
                topicId,
                "a".repeat(64),
                now
        );
        JevModerationRun run = JevModerationRun.preview(
                postId,
                topicId,
                "초록",
                "chalkak/posts/dev/original/photo.webp",
                admin,
                category.getId(),
                draft,
                now
        );
        given(actorResolver.resolve()).willReturn(new AuthenticatedAdmin(admin));
        given(service.createPreview(
                any(),
                any(),
                any(),
                any()
        )).willReturn(run);
        given(service.getCategory(run.getId())).willReturn(category);
        // When & Then
        mvc.perform(post("/api/v1/admin/jev-previews").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new JevPreviewCreateRequest(
                        postId.toString(),
                        topicId.toString(),
                        draft
                ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(run.getId().toString()))
                .andExpect(jsonPath("$.preview").value(true))
                .andExpect(jsonPath("$.finishedAt").isEmpty());
    }

    @Test
    @DisplayName("필수 식별자와 초안이 없으면 작업을 등록하지 않는다")
    void createPreview_missingFields_returnsBadRequest() throws Exception {
        // Given
        given(actorResolver.resolve()).willReturn(new AuthenticatedAdmin(UUID.randomUUID()));
        // When & Then
        mvc.perform(post("/api/v1/admin/jev-previews").contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
