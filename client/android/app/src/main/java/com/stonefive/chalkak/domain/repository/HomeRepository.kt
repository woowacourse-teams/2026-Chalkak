package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.PhotoContent
import com.stonefive.chalkak.domain.model.PhotoSort

interface HomeRepository {
    suspend fun getHome(sort: PhotoSort): PhotoContent

    suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): Int
}
