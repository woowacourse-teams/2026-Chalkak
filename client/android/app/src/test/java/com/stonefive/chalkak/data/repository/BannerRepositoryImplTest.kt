package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.banner.BannerApi
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class BannerRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: BannerRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit
            .Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BannerApi::class.java)
        repository = BannerRepositoryImpl(api, ApiRequestExecutor(json))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `latest banner request maps response fields`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
            {
              "currentPage": 1, "pageSize": 1, "hasNext": true,
              "banners": [{
                "id": "banner-id", "title": "제목", "subtitle": "설명",
                "webViewUrl": "https://example.com/issue",
                "thumbnailUrl": "https://example.com/banner.jpg",
                "createdAt": "2026-10-20T08:08:11.001Z"
              }]
            }
                """.trimIndent(),
            ),
        )
        val page = (repository.getBanners() as HomeResult.Success).value
        val request = server.takeRequest()
        assertEquals("/api/v1/banners?page=1&pageSize=1", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals(1, page.currentPage)
        assertEquals(1, page.pageSize)
        assertTrue(page.hasNext)
        val banner = page.banners.single()
        assertEquals("banner-id", banner.id)
        assertEquals("제목", banner.title)
        assertEquals("설명", banner.subtitle)
        assertEquals("https://example.com/issue", banner.webViewUrl)
        assertEquals("https://example.com/banner.jpg", banner.thumbnailUrl)
        assertEquals("2026-10-20T08:08:11.001Z", banner.createdAt)
    }

    @Test
    fun `empty and out of range page remain successful empty responses`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
            {"currentPage": 3, "pageSize": 1, "hasNext": false, "banners": []}
                """.trimIndent(),
            ),
        )
        val page = (repository.getBanners(page = 3) as HomeResult.Success).value
        assertTrue(page.banners.isEmpty())
        assertFalse(page.hasNext)
        assertEquals(3, page.currentPage)
    }

    @Test
    fun `HTTP errors retain status code`() = runTest {
        for (status in listOf(400, 401, 403, 500)) {
            server.enqueue(
                MockResponse().setResponseCode(status).setBody(
                    """
                {"errorCode": "ERROR", "message": "실패"}
                    """.trimIndent(),
                ),
            )
            assertEquals(HomeResult.Failure(HomeFailure.Http(status)), repository.getBanners())
        }
    }

    @Test
    fun `malformed response is not treated as an empty banner list`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        assertEquals(HomeResult.Failure(HomeFailure.InvalidResponse), repository.getBanners())
    }
}
