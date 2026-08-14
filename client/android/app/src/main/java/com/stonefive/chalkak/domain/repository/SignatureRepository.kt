package com.stonefive.chalkak.domain.repository

interface SignatureRepository {
    suspend fun uploadSignature(signaturePng: ByteArray)
}
