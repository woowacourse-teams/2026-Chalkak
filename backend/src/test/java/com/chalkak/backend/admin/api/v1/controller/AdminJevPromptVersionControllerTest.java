package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.service.jev.AdminJevPromptVersionService;
import com.chalkak.backend.admin.service.jev.AdminJevPromptVersionService.CreateCommand;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.jev.domain.JevPromptFixtures;
import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AdminJevPromptVersionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminJevPromptVersionControllerTest {

    private static final String PATH = "/api/v1/admin/jev-prompt-versions";
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @MockitoBean
    private AdminJevPromptVersionService service;
    @MockitoBean
    private AdminActorResolver actorResolver;

    @BeforeEach
    void setUp() {
        given(actorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("관리자 입력을 저장하면 비활성 버전 전체를 201로 반환한다")
    void createVersion_validInput_returnsInactiveCreatedVersion() throws Exception {
        // Given
        JevPromptVersion version = version();
        given(service.createVersion(eq(ADMIN_ID), any())).willReturn(version);

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateCommand(
                        JevPromptRole.JEV_PHOTO_MODERATION,
                        "jev-1.14.0",
                        JevPromptFixtures.photo(),
                        null
                ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(VERSION_ID.toString()))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.configuration.questions.topic_fit.type").value("choice"))
                .andExpect(jsonPath("$.systemPrompt").doesNotExist());
    }

    @Test
    @DisplayName("비활성화 요청에서 active를 생략하면 잘못된 요청으로 거절한다")
    void updateActivation_missingActive_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(put(PATH + "/" + VERSION_ID + "/activation")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("명시적 적용 요청을 서비스에 전달하고 결과를 반환한다")
    void updateActivation_explicitTrue_returnsAppliedVersion() throws Exception {
        // Given
        given(service.updateActivation(
                ADMIN_ID,
                VERSION_ID,
                true
        )).willReturn(version());

        // When & Then
        mockMvc.perform(put(PATH + "/" + VERSION_ID + "/activation")
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("JEV_PHOTO_MODERATION"));
    }

    @Test
    @DisplayName("잘못된 프롬프트 UUID와 역할은 400으로 거절한다")
    void getVersion_invalidIdOrRole_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get(PATH + "/invalid-id")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("role", "PHOTO_REVIEW")).andExpect(status().isBadRequest());
    }

    private JevPromptVersion version() {
        JevPromptVersion result = JevPromptVersion.create(
                JevPromptRole.JEV_PHOTO_MODERATION,
                "jev-1.14.0",
                JevPromptFixtures.photo(),
                1,
                null,
                ADMIN_ID
        );
        ReflectionTestUtils.setField(
                result,
                "id",
                VERSION_ID
        );
        return result;
    }
}
