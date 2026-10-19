package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.CreateCommand;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.VersionListResult;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.VersionResult;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.VersionSummary;
import com.chalkak.backend.aireview.domain.AiPromptParameters;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ReasoningEffort;
import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAiPromptVersionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminAiPromptVersionControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000901");
    private static final UUID VERSION_ID = UUID.fromString("019a0000-0000-7000-8000-000000000688");
    private static final String PATH = "/api/v1/admin/ai-prompt-versions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAiPromptVersionService versionService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("생성하면 201과 저장된 버전 전체를 응답한다")
    void createVersion_valid_returnsCreated() throws Exception {
        // Given
        given(versionService.createVersion(eq(ADMIN_ID), any())).willReturn(new VersionResult(
                VERSION_ID, AiPromptType.PHOTO_REVIEW, 3, "시스템", "{{topicTitle}}", "gpt-6.1-sol",
                new AiPromptParameters(ImageDetail.LOW, 700, ReasoningEffort.HIGH, 6.5), "1",
                "메모", ADMIN_ID, Instant.parse("2026-10-19T01:00:00Z")));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.promptVersionId").value(VERSION_ID.toString()))
                .andExpect(jsonPath("$.type").value("PHOTO_REVIEW"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.systemPrompt").value("시스템"))
                .andExpect(jsonPath("$.userPromptTemplate").value("{{topicTitle}}"))
                .andExpect(jsonPath("$.model").value("gpt-6.1-sol"))
                .andExpect(jsonPath("$.parameters.imageDetail").value("LOW"))
                .andExpect(jsonPath("$.parameters.maxOutputTokens").value(700))
                .andExpect(jsonPath("$.parameters.reasoningEffort").value("HIGH"))
                .andExpect(jsonPath("$.parameters.distinctivePerspectiveThreshold").value(6.5))
                .andExpect(jsonPath("$.outputSchemaVersion").value("1"))
                .andExpect(jsonPath("$.memo").value("메모"))
                .andExpect(jsonPath("$.createdByAdminId").value(ADMIN_ID.toString()))
                .andExpect(jsonPath("$.createdAt").value("2026-10-19T01:00:00Z"));
    }

    @Test
    @DisplayName("요청 필드는 서비스 명령으로 변환해 전달한다")
    void createVersion_validRequest_passesCommandToService() throws Exception {
        // Given
        given(versionService.createVersion(eq(ADMIN_ID), any())).willReturn(result());

        // When
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"PHOTO_REVIEW","systemPrompt":"시스템","userPromptTemplate":"{{topicTitle}}",
                 "model":"gpt-6.1-sol",
                 "parameters":{"imageDetail":"LOW","maxOutputTokens":700,"reasoningEffort":"HIGH",
                               "distinctivePerspectiveThreshold":6.5},
                 "memo":"메모"}
                """)).andExpect(status().isCreated());

        // Then
        verify(versionService).createVersion(ADMIN_ID, new CreateCommand(
                AiPromptType.PHOTO_REVIEW, "시스템", "{{topicTitle}}", "gpt-6.1-sol",
                ImageDetail.LOW, 700, ReasoningEffort.HIGH, 6.5, "메모"));
    }

    @Test
    @DisplayName("parameters와 memo를 생략해도 요청을 받는다")
    void createVersion_withoutOptionalFields_isAccepted() throws Exception {
        // Given
        given(versionService.createVersion(eq(ADMIN_ID), any())).willReturn(result());

        // When
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated());

        // Then
        verify(versionService).createVersion(ADMIN_ID, new CreateCommand(
                AiPromptType.PHOTO_REVIEW, "시스템", "{{topicTitle}}", "gpt-6.1-sol",
                null, null, null, null, null));
    }

    @Test
    @DisplayName("목록은 기본 페이지로 조회하고 본문 없이 요약과 페이지 메타데이터를 반환한다")
    void getVersions_defaultParameters_returnsSummaries() throws Exception {
        // Given
        given(versionService.getVersions(AiPromptType.PHOTO_REVIEW, 1, 20))
                .willReturn(new VersionListResult(1, 20, true, List.of(new VersionSummary(
                        VERSION_ID, AiPromptType.PHOTO_REVIEW, 3, "gpt-6.1-sol", "1", "메모",
                        ADMIN_ID, Instant.parse("2026-10-19T01:00:00Z")))));

        // When & Then
        mockMvc.perform(get(PATH).param("type", "PHOTO_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.promptVersions[0].promptVersionId")
                        .value(VERSION_ID.toString()))
                .andExpect(jsonPath("$.promptVersions[0].type").value("PHOTO_REVIEW"))
                .andExpect(jsonPath("$.promptVersions[0].version").value(3))
                .andExpect(jsonPath("$.promptVersions[0].model").value("gpt-6.1-sol"))
                .andExpect(jsonPath("$.promptVersions[0].outputSchemaVersion").value("1"))
                .andExpect(jsonPath("$.promptVersions[0].memo").value("메모"))
                .andExpect(jsonPath("$.promptVersions[0].createdByAdminId")
                        .value(ADMIN_ID.toString()))
                .andExpect(jsonPath("$.promptVersions[0].createdAt").value("2026-10-19T01:00:00Z"))
                .andExpect(jsonPath("$.promptVersions[0].systemPrompt").doesNotExist())
                .andExpect(jsonPath("$.promptVersions[0].userPromptTemplate").doesNotExist())
                .andExpect(jsonPath("$.promptVersions[0].parameters").doesNotExist());
    }

    @Test
    @DisplayName("명시한 페이지와 크기로 빈 목록을 조회할 수 있다")
    void getVersions_explicitParameters_returnsEmptyPage() throws Exception {
        // Given
        given(versionService.getVersions(AiPromptType.PHOTO_REVIEW, 2, 100))
                .willReturn(new VersionListResult(2, 100, false, List.of()));

        // When & Then
        mockMvc.perform(get(PATH).param("type", "PHOTO_REVIEW").param("page", "2")
                        .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.pageSize").value(100))
                .andExpect(jsonPath("$.promptVersions").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "page, abc",
            "pageSize, abc",
            "page, 2147483648"
    })
    @DisplayName("바인딩할 수 없는 쿼리 값은 400이다")
    void getVersions_invalidParameterType_returnsBadRequest(String parameter, String value)
            throws Exception {
        // When & Then
        mockMvc.perform(get(PATH).param("type", "PHOTO_REVIEW").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UNKNOWN", "photo_review"})
    @DisplayName("type이 비어 있거나 알 수 없는 값이면 400이다")
    void getVersions_blankOrUnknownType_returnsBadRequest(String type) throws Exception {
        // When & Then
        mockMvc.perform(get(PATH).param("type", type))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("type 파라미터를 아예 보내지 않아도 400이다")
    void getVersions_noTypeParameter_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get(PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("목록 조회 서비스의 비즈니스 예외는 400으로 변환한다")
    void getVersions_invalidPaging_returnsBusinessError() throws Exception {
        // Given
        given(versionService.getVersions(AiPromptType.PHOTO_REVIEW, 1, 101)).willThrow(
                new BusinessException(ErrorCode.BUSINESS_ERROR, "조회 조건이 올바르지 않습니다."));

        // When & Then
        mockMvc.perform(get(PATH).param("type", "PHOTO_REVIEW").param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("조회 조건이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("상세는 프롬프트 본문과 옵션까지 전체를 반환한다")
    void getVersion_existing_returnsFullVersion() throws Exception {
        // Given
        given(versionService.getVersion(VERSION_ID)).willReturn(result());

        // When & Then
        mockMvc.perform(get(PATH + "/" + VERSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promptVersionId").value(VERSION_ID.toString()))
                .andExpect(jsonPath("$.systemPrompt").exists())
                .andExpect(jsonPath("$.userPromptTemplate").exists())
                .andExpect(jsonPath("$.parameters.imageDetail").exists())
                .andExpect(jsonPath("$.outputSchemaVersion").value("1"));
    }

    @Test
    @DisplayName("없는 버전 상세는 404다")
    void getVersion_unknown_returnsNotFound() throws Exception {
        // Given
        given(versionService.getVersion(VERSION_ID)).willThrow(
                new NotFoundException(ErrorCode.BUSINESS_ERROR, "프롬프트 버전을 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(get(PATH + "/" + VERSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("프롬프트 버전을 찾을 수 없습니다."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "0198A0000-0000-7000-8000-000000000688"})
    @DisplayName("UUID 형식이 아닌 상세 경로는 400이다")
    void getVersion_invalidUuid_returnsBadRequest(String id) throws Exception {
        // When & Then
        mockMvc.perform(get(PATH + "/" + id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"systemPrompt\":\"a\",\"userPromptTemplate\":\"b\",\"model\":\"m\"}",
            "{\"type\":\"PHOTO_REVIEW\",\"userPromptTemplate\":\"b\",\"model\":\"m\"}",
            "{\"type\":\"PHOTO_REVIEW\",\"systemPrompt\":\" \",\"userPromptTemplate\":\"b\",\"model\":\"m\"}",
            "{\"type\":\"PHOTO_REVIEW\",\"systemPrompt\":\"a\",\"model\":\"m\"}",
            "{\"type\":\"PHOTO_REVIEW\",\"systemPrompt\":\"a\",\"userPromptTemplate\":\" \",\"model\":\"m\"}",
            "{\"type\":\"PHOTO_REVIEW\",\"systemPrompt\":\"a\",\"userPromptTemplate\":\"b\"}",
            "{\"type\":\"PHOTO_REVIEW\",\"systemPrompt\":\"a\",\"userPromptTemplate\":\"b\",\"model\":\" \"}"
    })
    @DisplayName("필수 필드가 없거나 비어 있으면 400이다")
    void createVersion_missingRequiredField_returnsBadRequest(String body) throws Exception {
        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @ParameterizedTest
    @MethodSource("tooLongReplacements")
    @DisplayName("길이 제한을 넘으면 400이다")
    void createVersion_tooLong_returnsBadRequest(String target, String replacement)
            throws Exception {
        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(validBody().replace(target, replacement)))
                .andExpect(status().isBadRequest());
    }

    private static Stream<Arguments> tooLongReplacements() {
        return Stream.of(
                Arguments.of("\"시스템\"", "\"" + "가".repeat(50_001) + "\""),
                Arguments.of("\"{{topicTitle}}\"", "\"" + "가".repeat(2_001) + "\""),
                Arguments.of("\"gpt-6.1-sol\"", "\"" + "m".repeat(101) + "\""),
                Arguments.of("\"gpt-6.1-sol\"",
                        "\"gpt-6.1-sol\",\"memo\":\"" + "가".repeat(501) + "\""));
    }

    @Test
    @DisplayName("길이 상한 경계값은 받는다")
    void createVersion_maxLengths_isAccepted() throws Exception {
        // Given
        given(versionService.createVersion(eq(ADMIN_ID), any())).willReturn(result());
        String body = validBody()
                .replace("\"시스템\"", "\"" + "가".repeat(50_000) + "\"")
                .replace("\"{{topicTitle}}\"", "\"" + "가".repeat(2_000) + "\"")
                .replace("\"gpt-6.1-sol\"", "\"" + "m".repeat(100) + "\",\"memo\":\""
                        + "가".repeat(500) + "\"");

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"type\":\"PHOTO_REVIEW\"|\"type\":\"UNKNOWN\"",
            "\"model\":\"gpt-6.1-sol\"|\"model\":\"gpt-6.1-sol\",\"parameters\":{\"imageDetail\":\"MEDIUM\"}",
            "\"model\":\"gpt-6.1-sol\"|\"model\":\"gpt-6.1-sol\",\"parameters\":{\"reasoningEffort\":\"MAX\"}"
    })
    @DisplayName("알 수 없는 type이나 enum 값은 400이다")
    void createVersion_unknownEnumValue_returnsBadRequest(String replacement) throws Exception {
        // Given
        String[] parts = replacement.split("\\|");

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(validBody().replace(parts[0], parts[1])))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("서비스의 400 예외는 BUSINESS_ERROR 400으로 변환한다")
    void createVersion_serviceBusinessException_returnsBadRequest() throws Exception {
        // Given
        given(versionService.createVersion(eq(ADMIN_ID), any())).willThrow(
                new BusinessException(ErrorCode.BUSINESS_ERROR, "허용되지 않은 모델입니다."));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("허용되지 않은 모델입니다."));
    }

    private VersionResult result() {
        return new VersionResult(VERSION_ID, AiPromptType.PHOTO_REVIEW, 1, "시스템",
                "{{topicTitle}}", "gpt-6.1-sol", new AiPromptParameters(null, null, null, null),
                "1", null, ADMIN_ID, Instant.parse("2026-10-19T01:00:00Z"));
    }

    private String validBody() {
        return """
                {"type":"PHOTO_REVIEW","systemPrompt":"시스템","userPromptTemplate":"{{topicTitle}}",
                 "model":"gpt-6.1-sol"}
                """;
    }
}
