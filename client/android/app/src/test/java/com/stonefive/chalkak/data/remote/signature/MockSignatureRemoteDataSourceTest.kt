package com.stonefive.chalkak.data.remote.signature

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockSignatureRemoteDataSourceTest {
    private val dataSource = MockSignatureRemoteDataSource()

    @Test
    fun `서명 PNG 업로드 시 목 URL을 반환하고 페이로드를 저장한다`() = runTest {
        val signaturePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        val response = dataSource.uploadSignature(signaturePng)

        assertEquals("mock://signature/0.png", response.signatureUrl)
        assertEquals(1, dataSource.uploadedSignatureCount)
        assertArrayEquals(signaturePng, dataSource.uploadedSignatureAt(0))
    }

    @Test
    fun `저장한 서명 PNG 페이로드는 호출자의 배열 변경에 영향받지 않는다`() = runTest {
        val signaturePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        dataSource.uploadSignature(signaturePng)
        signaturePng[0] = 0x00

        assertArrayEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            dataSource.uploadedSignatureAt(0),
        )
    }

    @Test
    fun `빈 서명 PNG 페이로드는 거부한다`() = runTest {
        val failure = runCatching {
            dataSource.uploadSignature(ByteArray(0))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}
