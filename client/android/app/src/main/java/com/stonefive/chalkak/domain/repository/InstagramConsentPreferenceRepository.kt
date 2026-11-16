package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.InstagramConsent

interface InstagramConsentPreferenceRepository {
    suspend fun get(userId: String): InstagramConsent?

    suspend fun save(
        userId: String,
        consent: InstagramConsent,
    )

    suspend fun clear(userId: String)
}
