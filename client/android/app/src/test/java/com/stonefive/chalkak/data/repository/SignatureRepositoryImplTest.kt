package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.signature.SignatureRemoteDataSource
import com.stonefive.chalkak.data.remote.signature.model.SignatureUploadResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SignatureRepositoryImplTest {
    private val remoteDataSource = RecordingSignatureRemoteDataSource()
    private val repository = SignatureRepositoryImpl(remoteDataSource)

    @Test
    fun `서명 PNG 업로드 시 원격 데이터 소스에 페이로드를 전달한다`() = runTest {
        val signaturePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        repository.uploadSignature(signaturePng)

        assertArrayEquals(signaturePng, remoteDataSource.uploadedSignaturePng)
    }
}

private class RecordingSignatureRemoteDataSource : SignatureRemoteDataSource {
    var uploadedSignaturePng: ByteArray = ByteArray(0)

    override suspend fun uploadSignature(signaturePng: ByteArray): SignatureUploadResponse {
        uploadedSignaturePng = signaturePng
        return SignatureUploadResponse(signatureUrl = "https://example.com/signature.png")
    }
}
