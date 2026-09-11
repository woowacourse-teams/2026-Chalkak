package com.stonefive.chalkak.data.remote.post

import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OkHttpPostImageUploaderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `WebP 파일을 인증 헤더 없는 raw PUT body로 업로드한다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val file = File.createTempFile("post-image-upload-test", ".webp")
        val bytes = byteArrayOf(1, 2, 3, 4)
        file.writeBytes(bytes)

        try {
            val uploader = OkHttpPostImageUploader(OkHttpClient())
            val result = uploader.upload(
                uploadUrl = server.url("/post").toString(),
                contentType = "image/webp",
                imageFile = file,
            )
            val request = server.takeRequest()

            assertEquals(PostImageUploadResult.Success, result)
            assertEquals("PUT", request.method)
            assertEquals("image/webp", request.headers["Content-Type"])
            assertNull(request.headers["Authorization"])
            assertNull(request.headers["X-User-Id"])
            assertArrayEquals(bytes, request.body.readByteArray())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `잘못된 Content-Type은 요청 없이 실패시킨다`() = runTest {
        val file = File.createTempFile("post-image-upload-test", ".webp")
        file.writeBytes(byteArrayOf(1))

        try {
            val uploader = OkHttpPostImageUploader(OkHttpClient())
            val result = uploader.upload(
                uploadUrl = server.url("/post").toString(),
                contentType = "not a media type",
                imageFile = file,
            )

            assertEquals(PostImageUploadResult.InvalidUploadRequest, result)
            assertEquals(0, server.requestCount)
        } finally {
            file.delete()
        }
    }
}
