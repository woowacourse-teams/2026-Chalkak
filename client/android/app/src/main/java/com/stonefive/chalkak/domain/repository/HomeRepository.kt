package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.HomeContent
import com.stonefive.chalkak.domain.model.HomeSort

interface HomeRepository {
    suspend fun getHome(sort: HomeSort): HomeContent

    suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): Int
}
