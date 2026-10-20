package com.chalkak.backend.admin.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AdminArgumentResolverWebMvcConfig;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptPreviewService;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptPreviewService.PreviewCommand;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptPreviewService.PreviewResult;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ReasoningEffort;
import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiReviewResult;
import com.chalkak.backend.aireview.domain.PhotoReviewOutput;
import com.chalkak.backend.aireview.service.AiReviewGeneration.Usage;
import com.chalkak.backend.aireview.service.AiReviewGenerationException.FailureType;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAiPromptPreviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminArgumentResolverWebMvcConfig.class})
class AdminAiPromptPreviewControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000901");
    private static final UUID POST_ID = UUID.fromString("019a0000-0000-7000-8000-000000000687");
    private static final String PATH = "/api/v1/admin/ai-prompt-previews";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAiPromptPreviewService previewService;

    @MockitoBean
    private AdminActorResolver adminActorResolver;

    @BeforeEach
    void setUp() {
        given(adminActorResolver.resolve()).willReturn(new AuthenticatedAdmin(ADMIN_ID));
    }

    @Test
    @DisplayName("성공 결과는 200과 변환 결과 원본 점수 사용량을 직렬화한다")
    void preview_succeeded_serializesOutcome() throws Exception {
        // Given
        PhotoReviewOutput output = new PhotoReviewOutput(
                8.72,
                8.0,
                "구도가 좋습니다."
        );
        AiReviewResult result = output.toResult(7.0);
        given(previewService.preview(eq(ADMIN_ID), any())).willReturn(new PreviewResult(
                true,
                null,
                null,
                result,
                output,
                new Usage(
                        1200,
                        300,
                        85,
                        40
                ),
                1234L,
                "resp_1"
        ));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(true))
                .andExpect(jsonPath("$.failureType").doesNotExist())
                .andExpect(jsonPath("$.retryable").doesNotExist())
                .andExpect(jsonPath("$.result.overallScore").value(8.7))
                .andExpect(jsonPath("$.result.criteria[0].key").value("COMPOSITION"))
                .andExpect(jsonPath("$.result.criteria[0].label").value("구도"))
                .andExpect(jsonPath("$.result.criteria[0].score").value(8.7))
                .andExpect(jsonPath("$.result.distinctivePerspective").value(true))
                .andExpect(jsonPath("$.result.feedback").value("구도가 좋습니다."))
                .andExpect(jsonPath("$.output.compositionScore").value(8.72))
                .andExpect(jsonPath("$.output.themeExpressionScore").value(8.0))
                .andExpect(jsonPath("$.usage.inputTokens").value(1200))
                .andExpect(jsonPath("$.usage.cachedInputTokens").value(300))
                .andExpect(jsonPath("$.usage.outputTokens").value(85))
                .andExpect(jsonPath("$.usage.reasoningTokens").value(40))
                .andExpect(jsonPath("$.latencyMs").value(1234))
                .andExpect(jsonPath("$.responseId").value("resp_1"));
    }

    @Test
    @DisplayName("실패 결과도 200으로 실패 유형 재시도 여부를 직렬화하고 값이 없는 항목은 null이다")
    void preview_failed_serializesFailureOutcome() throws Exception {
        // Given
        given(previewService.preview(eq(ADMIN_ID), any())).willReturn(new PreviewResult(
                false,
                FailureType.TIMEOUT,
                true,
                null,
                null,
                null,
                null,
                null
        ));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(false))
                .andExpect(jsonPath("$.failureType").value("TIMEOUT"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.usage").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.latencyMs").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("요청 필드는 서비스 명령으로 변환해 전달한다")
    void preview_validRequest_passesCommandToService() throws Exception {
        // Given
        given(previewService.preview(eq(ADMIN_ID), any())).willReturn(new PreviewResult(
                false,
                FailureType.TIMEOUT,
                true,
                null,
                null,
                null,
                null,
                null
        ));

        // When
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"PHOTO_REVIEW","systemPrompt":"시스템","userPromptTemplate":"{{topicTitle}}",
                 "model":"gpt-6.1-sol",
                 "parameters":{"imageDetail":"LOW","maxOutputTokens":700,"reasoningEffort":"HIGH",
                               "distinctivePerspectiveThreshold":6.5},
                 "postId":"%s"}
                """.formatted(POST_ID))).andExpect(status().isOk());

        // Then
        verify(previewService).preview(ADMIN_ID, new PreviewCommand(
                AiPromptType.PHOTO_REVIEW,
                "시스템",
                "{{topicTitle}}",
                "gpt-6.1-sol",
                ImageDetail.LOW,
                700,
                ReasoningEffort.HIGH,
                6.5,
                POST_ID
        ));
    }

    @Test
    @DisplayName("parameters를 생략해도 요청을 받는다")
    void preview_withoutParameters_isAccepted() throws Exception {
        // Given
        given(previewService.preview(eq(ADMIN_ID), any())).willReturn(new PreviewResult(
                false,
                FailureType.TIMEOUT,
                true,
                null,
                null,
                null,
                null,
                null
        ));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"systemPrompt\":\"a\",\"userPromptTemplate\":\"b\",\"model\":\"m\",\"postId\":\"019a0000-0000-7000-8000-000000000687\"}",
            "{\"type\":\"PHOTO_REVIEW\",\"userPromptTemplate\":\"b\",\"model\":\"m\",\"postId\":\"019a0000-0000-7000-8000-000000000687\"}",
            "{\"type\":\"PHOTO_REVIEW\",\"systemPrompt\":\" \",\"userPromptTemplate\":\"b\",\"model\":\"m\",\"postId\":\"019a0000-0000-7000-8000-000000000687\"}",
            "{\"type\":\"PHOTO_REVIEW\",\"systemPrompt\":\"a\",\"model\":\"m\",\"postId\":\"019a0000-0000-7000-8000-000000000687\"}",
            "{\"type\":\"PHOTO_REVIEW\",\"systemPrompt\":\"a\",\"userPromptTemplate\":\"b\",\"postId\":\"019a0000-0000-7000-8000-000000000687\"}",
            "{\"type\":\"PHOTO_REVIEW\",\"systemPrompt\":\"a\",\"userPromptTemplate\":\"b\",\"model\":\"m\"}"
    })
    @DisplayName("필수 필드가 없거나 비어 있으면 400이다")
    void preview_missingRequiredField_returnsBadRequest(String body) throws Exception {
        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("systemPrompt가 5만 자를 넘으면 400이다")
    void preview_tooLongSystemPrompt_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(
                validBody().replace("\"시스템\"", "\"" + "가".repeat(50_001) + "\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("systemPrompt가 정확히 5만 자이면 받는다")
    void preview_maxLengthSystemPrompt_isAccepted() throws Exception {
        // Given
        given(previewService.preview(eq(ADMIN_ID), any())).willReturn(new PreviewResult(
                false,
                FailureType.TIMEOUT,
                true,
                null,
                null,
                null,
                null,
                null
        ));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(
                validBody().replace("\"시스템\"", "\"" + "가".repeat(50_000) + "\"")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("userPromptTemplate이 2000자를 넘으면 400이다")
    void preview_tooLongTemplate_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(
                validBody().replace("\"{{topicTitle}}\"", "\"" + "가".repeat(2_001) + "\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("postId가 UUID 형식이 아니면 400이다")
    void preview_invalidPostId_returnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(
                validBody().replace(POST_ID.toString(), "not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"type\":\"PHOTO_REVIEW\"|\"type\":\"UNKNOWN\"",
            "\"model\":\"gpt-6.1-sol\"|\"model\":\"gpt-6.1-sol\",\"parameters\":{\"imageDetail\":\"MEDIUM\"}",
            "\"model\":\"gpt-6.1-sol\"|\"model\":\"gpt-6.1-sol\",\"parameters\":{\"reasoningEffort\":\"MAX\"}"
    })
    @DisplayName("알 수 없는 type이나 enum 값은 400이다")
    void preview_unknownEnumValue_returnsBadRequest(String replacement) throws Exception {
        // Given
        String[] parts = replacement.split("\\|");

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(validBody().replace(parts[0], parts[1])))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("서비스의 400 예외는 BUSINESS_ERROR 400으로 변환한다")
    void preview_serviceBusinessException_returnsBadRequest() throws Exception {
        // Given
        given(previewService.preview(eq(ADMIN_ID), any())).willThrow(
                new BusinessException(ErrorCode.BUSINESS_ERROR, "허용되지 않은 모델입니다."));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("허용되지 않은 모델입니다."));
    }

    @Test
    @DisplayName("서비스의 404 예외는 404로 변환한다")
    void preview_serviceNotFound_returnsNotFound() throws Exception {
        // Given
        given(previewService.preview(eq(ADMIN_ID), any())).willThrow(
                new NotFoundException(ErrorCode.BUSINESS_ERROR, "게시물을 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isNotFound());
    }

    private String validBody() {
        return """
                {"type":"PHOTO_REVIEW","systemPrompt":"시스템","userPromptTemplate":"{{topicTitle}}",
                 "model":"gpt-6.1-sol","postId":"%s"}
                """.formatted(POST_ID);
    }
}
