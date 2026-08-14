package com.stonefive.chalkak.data.remote.signature

import com.stonefive.chalkak.data.remote.signature.model.SignatureUploadResponse
import kotlinx.coroutines.delay

class MockSignatureRemoteDataSource(private val responseDelayMillis: Long = 0L) : SignatureRemoteDataSource {
    private val uploadedSignatures = mutableListOf<ByteArray>()

    val uploadedSignatureCount: Int
        get() = uploadedSignatures.size

    override suspend fun uploadSignature(signaturePng: ByteArray): SignatureUploadResponse {
        delay(responseDelayMillis)
        require(signaturePng.isNotEmpty()) { "서명 PNG 데이터가 비어 있습니다." }

        uploadedSignatures += signaturePng.copyOf()

        return SignatureUploadResponse(
            signatureUrl = "mock://signature/${uploadedSignatures.lastIndex}.png",
        )
    }

    fun uploadedSignatureAt(index: Int): ByteArray = uploadedSignatures[index].copyOf()
}
