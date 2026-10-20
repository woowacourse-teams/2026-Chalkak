package com.chalkak.backend.banner.api.v1.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.banner.service.BannerListResult;
import com.chalkak.backend.banner.service.BannerResult;
import com.chalkak.backend.banner.service.BannerService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BannerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BannerControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private BannerService bannerService;

    @Test
    @DisplayName("기본 페이지로 조회하면 페이지 정보와 배너 여섯 필드를 반환한다")
    void getBanners_defaultParameters_returnsBannerPage() throws Exception {
        // Given
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-19T00:00:00Z");
        given(bannerService.getBanners(1, 20)).willReturn(new BannerListResult(
                1,
                20,
                false,
                List.of(new BannerResult(
                        id,
                        "제목",
                        "부제목",
                        "https://example.com/event",
                        "https://example.com/image.png",
                        createdAt
                ))
        ));

        // When & Then
        mockMvc.perform(get("/api/v1/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.banners[0].id").value(id.toString()))
                .andExpect(jsonPath("$.banners[0].title").value("제목"))
                .andExpect(jsonPath("$.banners[0].subtitle").value("부제목"))
                .andExpect(jsonPath("$.banners[0].webViewUrl").value("https://example.com/event"))
                .andExpect(jsonPath("$.banners[0].thumbnailUrl")
                        .value("https://example.com/image.png"))
                .andExpect(jsonPath("$.banners[0].createdAt").value(createdAt.toString()));
    }

    @Test
    @DisplayName("명시한 페이지와 크기를 서비스에 전달하고 빈 목록을 반환한다")
    void getBanners_explicitParameters_returnsEmptyPage() throws Exception {
        // Given
        given(bannerService.getBanners(2, 100))
                .willReturn(new BannerListResult(
                        2,
                        100,
                        false,
                        List.of()
                ));

        // When & Then
        mockMvc.perform(get("/api/v1/banners").param("page", "2").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(2))
                .andExpect(jsonPath("$.pageSize").value(100))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.banners").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"page, abc", "pageSize, abc", "page, 2147483648"})
    @DisplayName("숫자로 바인딩할 수 없는 조회 조건은 400으로 반환한다")
    void getBanners_invalidParameterType_returnsBadRequest(String parameter, String value)
            throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/banners").param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @DisplayName("공통 조회 서비스의 검증 예외는 400으로 반환한다")
    void getBanners_invalidPagination_returnsBusinessError() throws Exception {
        // Given
        given(bannerService.getBanners(0, 20)).willThrow(
                new BusinessException(ErrorCode.BUSINESS_ERROR, "조회 조건이 올바르지 않습니다."));

        // When & Then
        mockMvc.perform(get("/api/v1/banners").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }
}
