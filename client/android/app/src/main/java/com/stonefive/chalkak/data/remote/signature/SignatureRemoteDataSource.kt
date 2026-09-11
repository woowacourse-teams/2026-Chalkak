package com.stonefive.chalkak.data.remote.signature

import com.stonefive.chalkak.data.remote.signature.model.SignatureUploadResponse

interface SignatureRemoteDataSource {
    suspend fun uploadSignature(signaturePng: ByteArray): SignatureUploadResponse
}
