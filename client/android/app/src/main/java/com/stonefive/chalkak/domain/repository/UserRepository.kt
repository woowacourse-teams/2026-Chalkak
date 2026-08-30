package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.SignatureUpdateResult
import com.stonefive.chalkak.domain.model.UserProfile

interface UserRepository {
    suspend fun getMySignature(): UserProfile

    suspend fun updateMySignature(signaturePng: ByteArray): SignatureUpdateResult

    suspend fun withdraw()
}
