package com.chalkak.backend.admin.api.v1.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.infrastructure.bootstrap.DevelopmentAdminBootstrap;
import com.chalkak.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class AdminTopicPaginationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DevelopmentAdminBootstrap developmentAdminBootstrap;

    @BeforeEach
    void setUp() {
        developmentAdminBootstrap.run(new DefaultApplicationArguments());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 21474836, 21474837})
    @DisplayName("일반 페이지와 조회 가능한 마지막 페이지까지 기존 응답 형식을 유지한다")
    void getTopics_validPage_returnsExistingPageContract(int page) throws Exception {
        // Given & When & Then
        mockMvc.perform(get("/api/v1/admin/topics")
                .queryParam("page", Integer.toString(page))
                .queryParam("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(page))
                .andExpect(jsonPath("$.pageSize").value(100))
                .andExpect(jsonPath("$.hasNext").isBoolean())
                .andExpect(jsonPath("$.topics").isArray());
    }

    @ParameterizedTest
    @ValueSource(ints = {21474838, Integer.MAX_VALUE, 42949674})
    @DisplayName("조회 범위를 넘는 페이지는 서버 오류 대신 기존 형식의 400 오류를 반환한다")
    void getTopics_offsetOverflow_returnsBusinessError(int page) throws Exception {
        // Given & When & Then
        mockMvc.perform(get("/api/v1/admin/topics")
                .queryParam("page", Integer.toString(page))
                .queryParam("pageSize", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("관리자 주제 요청이 올바르지 않습니다."));
    }
}
