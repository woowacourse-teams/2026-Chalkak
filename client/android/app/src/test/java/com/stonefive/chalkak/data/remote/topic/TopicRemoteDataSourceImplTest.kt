package com.stonefive.chalkak.data.remote.topic

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.topic.model.TopicResponse
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class TopicRemoteDataSourceImplTest {
    private lateinit var server: MockWebServer
    private lateinit var dataSource: TopicRemoteDataSourceImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit
            .Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        dataSource = TopicRemoteDataSourceImpl(
            topicApi = retrofit.create(TopicApi::class.java),
            requestExecutor = ApiRequestExecutor(json, onUnauthorized = {}),
        )
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `토픽 요청은 공급된 KST 날짜를 ISO 형식으로 전송한다`() = runTest {
        server.enqueue(jsonResponse(TOPIC_BODY))

        val result = dataSource.getTopic(LocalDate.of(2026, 8, 28))

        assertEquals(
            ApiResult.Success(TopicResponse("topic-id", "바다", "2026-08-28")),
            result,
        )
        assertEquals("/api/v1/topics?date=2026-08-28", server.takeRequest().path)
    }

    @Test
    fun `HTTP 실패 상태를 보존한다`() = runTest {
        listOf(404, 401, 500, 400).forEach { statusCode ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(statusCode)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"errorCode":"ERROR_$statusCode","message":"error $statusCode"}"""),
            )

            assertEquals(
                ApiResult.Failure(ApiError.Http(statusCode, "ERROR_$statusCode", "error $statusCode")),
                dataSource.getTopic(LocalDate.of(2026, 8, 28)),
            )
        }
    }

    @Test
    fun `HTTP 오류 body가 명세와 달라도 상태를 보존한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errorCode":"BUSINESS_ERROR"}"""),
        )

        assertEquals(
            ApiResult.Failure(ApiError.Http(400, null)),
            dataSource.getTopic(LocalDate.of(2026, 8, 28)),
        )
    }

    @Test
    fun `빈 성공 body와 깨진 JSON은 invalid response다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(jsonResponse("{"))

        assertEquals(
            ApiResult.Failure(ApiError.InvalidResponse),
            dataSource.getTopic(LocalDate.of(2026, 8, 28)),
        )
        assertEquals(
            ApiResult.Failure(ApiError.InvalidResponse),
            dataSource.getTopic(LocalDate.of(2026, 8, 28)),
        )
    }

    @Test
    fun `서버 연결 실패는 network failure다`() = runTest {
        server.shutdown()

        assertEquals(
            ApiResult.Failure(ApiError.Network),
            dataSource.getTopic(LocalDate.of(2026, 8, 28)),
        )
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val TOPIC_BODY =
            """{"id":"topic-id","title":"바다","topicDate":"2026-08-28","startsAt":"2026-08-27T15:00:00Z","endsAt":"2026-08-28T15:00:00Z","phase":"ACTIVE"}"""
    }
}
