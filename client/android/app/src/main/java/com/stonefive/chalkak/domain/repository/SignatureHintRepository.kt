package com.stonefive.chalkak.domain.repository

interface SignatureHintRepository {
    suspend fun claimFirstExposure(): Boolean
}
