package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.user.UserDataSource
import com.stonefive.chalkak.domain.model.UserProfile
import com.stonefive.chalkak.domain.model.UserProfileLoadException
import com.stonefive.chalkak.domain.model.UserProfileLoadFailure
import com.stonefive.chalkak.domain.repository.UserRepository

class UserRepositoryImpl(private val userDataSource: UserDataSource) : UserRepository {
    override suspend fun getMySignature(): UserProfile = when (
        val result = userDataSource.getMySignature()
    ) {
        is ApiResult.Success -> UserProfile(
            signatureUrl = result.value.originalImageUrl,
            signatureThumbnailUrl = result.value.thumbnailImageUrl,
        )

        is ApiResult.Failure -> throw UserProfileLoadException(result.error.toProfileLoadFailure())
    }

    private fun ApiError.toProfileLoadFailure(): UserProfileLoadFailure = when (this) {
        ApiError.Network -> UserProfileLoadFailure.NETWORK

        ApiError.InvalidResponse -> UserProfileLoadFailure.UNKNOWN

        is ApiError.Http -> when (statusCode) {
            401 -> UserProfileLoadFailure.UNAUTHORIZED
            403 -> UserProfileLoadFailure.FORBIDDEN
            else -> UserProfileLoadFailure.UNKNOWN
        }
    }
}
