package com.stonefive.chalkak.data.remote.home

import com.stonefive.chalkak.data.remote.home.model.HomeLikeResponse
import com.stonefive.chalkak.data.remote.home.model.HomeResponse
import com.stonefive.chalkak.domain.model.PhotoSort

interface HomeRemoteDataSource {
    suspend fun getHome(sort: PhotoSort): HomeResponse

    suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeLikeResponse
}
