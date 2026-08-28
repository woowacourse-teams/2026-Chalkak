package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.UserProfile

interface UserRepository {
    suspend fun getMySignature(): UserProfile
}
