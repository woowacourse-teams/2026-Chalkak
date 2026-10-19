package com.chalkak.backend.post.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.common.logging.RequestIdFilter;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.GlobalExceptionHandler;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.service.PostCommandService;
import com.chalkak.backend.post.service.PostListResult;
import com.chalkak.backend.post.service.PostQueryService;
import com.chalkak.backend.post.service.PostSort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PostFeedAccessLogTest {

    private static final LocalDate TOPIC_DATE = LocalDate.of(2026, 8, 12);
    private static final UUID FIRST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
    private static final UUID SECOND_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d5");

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PostCommandService postCommandService;

    @MockitoBean
    private PostQueryService postQueryService;

    private MockMvc mockMvc;
    private final Logger accessLogger = (Logger) LoggerFactory.getLogger("chalkak.access");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new RequestIdFilter()).build();
        appender.start();
        accessLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        accessLogger.detachAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {"recent", "popular", "random"})
    @DisplayName("정상 피드 응답은 조회 조건만 접근 로그 한 줄에 남기고 게시물 ID는 제외한다")
    void getPosts_validSort_logsConditionsWithoutPostIdsInOneAccessEvent(String sort)
            throws Exception {
        // Given
        PostSort postSort = PostSort.from(sort);
        List<PostListResult.PostSummary> posts = List.of(summary(SECOND_ID), summary(FIRST_ID));
        given(postQueryService.getPosts(TOPIC_DATE, postSort, null, 2, 20, Optional.empty()))
                .willReturn(new PostListResult(2, 20, false, null, posts));

        // When
        MvcResult result = mockMvc.perform(get("/api/v1/posts")
                .queryParam("topicDate", TOPIC_DATE.toString())
                .queryParam("sort", sort)
                .queryParam("page", "2")
                .queryParam("postIds", "untrusted-input"))
                .andExpect(status().isOk()).andReturn();

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(keyValue(event, "sort")).isEqualTo(sort);
        assertThat(keyValue(event, "page")).isEqualTo(2);
        assertThat(keyValue(event, "pageSize")).isEqualTo(20);
        for (org.slf4j.event.KeyValuePair pair : event.getKeyValuePairs()) {
            assertThat(pair.key).isNotEqualTo("postIds");
        }
        assertThat(keyValue(event, "topicDate")).isEqualTo(TOPIC_DATE.toString());
        assertThat(keyValue(event, "route")).isEqualTo("/api/v1/posts");
        assertThat(keyValue(event, "status")).isEqualTo(200);
        assertThat(keyValue(event, "durationMs")).isInstanceOf(Long.class);
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId",
                result.getResponse().getHeader("X-Request-Id"));
        assertThat(result.getResponse().getContentAsString())
                .contains(SECOND_ID.toString(), FIRST_ID.toString(), "likeCount")
                .doesNotContain("postIds", "sort");
        for (org.slf4j.event.KeyValuePair pair : event.getKeyValuePairs()) {
            assertThat(String.valueOf(pair.value))
                    .doesNotContain("private-title", "private-photo", "untrusted-input");
        }
    }

    @Test
    @DisplayName("기본 조건의 빈 피드는 recent와 기본 페이지 크기를 남긴다")
    void getPosts_emptyDefaultFeed_logsDefaultConditions() throws Exception {
        // Given
        given(postQueryService.getPosts(TOPIC_DATE, PostSort.RECENT, null, 1, 20, Optional.empty()))
                .willReturn(new PostListResult(1, 20, false, null, List.of()));

        // When
        mockMvc.perform(get("/api/v1/posts").queryParam("topicDate", TOPIC_DATE.toString()))
                .andExpect(status().isOk());

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(keyValue(event, "sort")).isEqualTo("recent");
        assertThat(keyValue(event, "page")).isEqualTo(1);
        assertThat(keyValue(event, "pageSize")).isEqualTo(20);
    }

    @Test
    @DisplayName("최대 페이지 크기도 조회 조건으로 기록하고 응답의 게시물 ID는 남기지 않는다")
    void getPosts_maximumPageSize_logsPageSizeWithoutPostIds() throws Exception {
        // Given
        List<PostListResult.PostSummary> posts = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            UUID id = UUID.randomUUID();
            posts.add(summary(id));
        }
        given(postQueryService.getPosts(TOPIC_DATE, PostSort.RECENT, null, 1, 100,
                Optional.empty()))
                .willReturn(new PostListResult(1, 100, true, null, posts));

        // When
        mockMvc.perform(get("/api/v1/posts").queryParam("topicDate", TOPIC_DATE.toString())
                .queryParam("pageSize", "100")).andExpect(status().isOk());

        // Then
        assertThat(appender.list).hasSize(1);
        for (org.slf4j.event.KeyValuePair pair : appender.list.getFirst().getKeyValuePairs()) {
            assertThat(pair.key).isNotEqualTo("postIds");
        }
        assertThat(keyValue(appender.list.getFirst(), "pageSize")).isEqualTo(100);
    }

    @ParameterizedTest
    @CsvSource({"sort, unknown", "page, 0", "pageSize, 101", "topicDate, not-a-date"})
    @DisplayName("잘못된 피드 조건은 400 접근 로그에 조회 메타데이터나 입력 원문을 남기지 않는다")
    void getPosts_invalidCondition_logsNoFeedMetadata(String parameter, String value)
            throws Exception {
        // Given
        var request = get("/api/v1/posts");
        if (!"topicDate".equals(parameter)) {
            request.queryParam("topicDate", TOPIC_DATE.toString());
        }
        request.queryParam(parameter, value);

        // When
        mockMvc.perform(request).andExpect(status().isBadRequest());

        // Then
        assertThat(appender.list).hasSize(1);
        assertNoFeedMetadata(appender.list.getFirst());
    }

    @Test
    @DisplayName("조회 서비스 실패 뒤 다음 성공 요청에도 이전 요청의 메타데이터가 섞이지 않는다")
    void getPosts_serviceFailsThenSucceeds_keepsRequestMetadataIsolated() throws Exception {
        // Given
        given(postQueryService.getPosts(any(), any(), any(), anyInt(), anyInt(), any()))
                .willThrow(new NotFoundException(ErrorCode.BUSINESS_ERROR, "주제가 없습니다."))
                .willReturn(new PostListResult(1, 20, false, null, List.of()));

        // When
        mockMvc.perform(get("/api/v1/posts").queryParam("topicDate", TOPIC_DATE.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/posts").queryParam("topicDate", TOPIC_DATE.toString()))
                .andExpect(status().isOk());

        // Then
        assertThat(appender.list).hasSize(2);
        assertNoFeedMetadata(appender.list.getFirst());
        assertThat(keyValue(appender.list.getLast(), "sort")).isEqualTo("recent");
        assertThat(appender.list.getFirst().getMDCPropertyMap().get("requestId"))
                .isNotEqualTo(appender.list.getLast().getMDCPropertyMap().get("requestId"));
    }

    private PostListResult.PostSummary summary(UUID id) {
        return new PostListResult.PostSummary(id, "private-photo", null, "signature", null,
                "private-title", Instant.parse("2026-08-12T03:30:00Z"), 43L, false, false);
    }

    private Object keyValue(ILoggingEvent event, String key) {
        for (org.slf4j.event.KeyValuePair pair : event.getKeyValuePairs()) {
            if (pair.key.equals(key)) {
                return pair.value;
            }
        }
        throw new AssertionError("로그 필드가 없습니다: " + key);
    }

    private void assertNoFeedMetadata(ILoggingEvent event) {
        for (org.slf4j.event.KeyValuePair pair : event.getKeyValuePairs()) {
            assertThat(pair.key).isNotIn("topicDate", "sort", "page", "pageSize", "postIds");
        }
    }
}
