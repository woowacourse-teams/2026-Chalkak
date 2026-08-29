package com.stonefive.chalkak.data.remote.signature

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

class OkHttpSignatureUploaderTest {
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
    fun `PNG를 인증 헤더 없는 raw PUT body로 업로드한다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val uploader = OkHttpSignatureUploader(OkHttpClient())
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        val result = uploader.upload(server.url("/signature").toString(), png)
        val request = server.takeRequest()

        assertEquals(SignatureUploadResult.Success, result)
        assertEquals("PUT", request.method)
        assertEquals("image/png", request.headers["Content-Type"])
        assertNull(request.headers["Authorization"])
        assertNull(request.headers["X-User-Id"])
        assertArrayEquals(png, request.body.readByteArray())
    }

    @Test
    fun `잘못된 업로드 URL은 명시적인 실패 결과로 변환한다`() = runTest {
        val uploader = OkHttpSignatureUploader(OkHttpClient())

        val result = uploader.upload("not-a-url", byteArrayOf(1, 2, 3))

        assertEquals(SignatureUploadResult.InvalidUploadUrl, result)
    }

    @Test
    fun `공통 업로더는 파일을 스트리밍하고 응답 Content-Type을 그대로 전송한다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val file = File.createTempFile("presigned-upload-test", ".webp")
        val bytes = byteArrayOf(1, 2, 3, 4)
        file.writeBytes(bytes)

        try {
            val uploader = OkHttpPresignedImageUploader(OkHttpClient())
            val result = uploader.upload(
                uploadUrl = server.url("/post").toString(),
                contentType = "image/webp",
                content = UploadContent.FileContent(file),
            )
            val request = server.takeRequest()

            assertEquals(PresignedUploadResult.Success, result)
            assertEquals("PUT", request.method)
            assertEquals("image/webp", request.headers["Content-Type"])
            assertArrayEquals(bytes, request.body.readByteArray())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `공통 업로더는 잘못된 Content-Type을 요청 없이 실패시킨다`() = runTest {
        val uploader = OkHttpPresignedImageUploader(OkHttpClient())

        val result = uploader.upload(
            uploadUrl = server.url("/post").toString(),
            contentType = "not a media type",
            content = UploadContent.Bytes(byteArrayOf(1)),
        )

        assertEquals(PresignedUploadResult.InvalidUploadUrl, result)
        assertEquals(0, server.requestCount)
    }
}
