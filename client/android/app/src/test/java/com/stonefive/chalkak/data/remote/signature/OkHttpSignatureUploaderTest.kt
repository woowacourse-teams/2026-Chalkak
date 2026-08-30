package com.stonefive.chalkak.data.remote.signature

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
}
