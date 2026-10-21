package com.stonefive.chalkak.data.remote.aireview

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
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

class AiReviewDataSourceImplTest {
    private lateinit var server: MockWebServer
    private lateinit var dataSource: AiReviewDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit
            .Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AiReviewApi::class.java)
        dataSource = AiReviewDataSourceImpl(api, ApiRequestExecutor(json))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `GET 리뷰 상태와 결과를 역직렬화한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "status":"COMPLETED",
                      "requestedAt":"2026-10-21T12:00:00Z",
                      "completedAt":"2026-10-21T12:00:21Z",
                      "result":{"overallScore":7.5,"criteria":[{"key":"COMPOSITION","label":"구도","score":7.5}],"distinctivePerspective":true,"feedback":{"summary":"좋아요","strength":"강점","weakness":"아쉬움","suggestion":"제안"}},
                      "canRequestAgain":false
                    }
                    """.trimIndent(),
                ),
        )

        val result = dataSource.getReview("post-id")

        assertEquals(
            "COMPLETED",
            (result as com.stonefive.chalkak.data.remote.ApiResult.Success).value.status,
        )
        assertEquals("/posts/post-id/ai-review", server.takeRequest().path)
    }

    @Test
    fun `POST 리뷰 요청은 본문 없이 전송한다`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setBody("{\"status\":\"PROCESSING\",\"canRequestAgain\":false}"),
        )

        val result = dataSource.requestReview("post-id")
        val request = server.takeRequest()

        assertEquals(
            "PROCESSING",
            (result as com.stonefive.chalkak.data.remote.ApiResult.Success).value.status,
        )
        assertEquals("POST", request.method)
        assertEquals("/posts/post-id/ai-review", request.path)
        assertEquals(0, request.body.size)
    }
}
