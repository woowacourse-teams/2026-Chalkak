package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostSort

interface HomeRepository {
    suspend fun getHome(sort: PostSort): PostContent

    suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): Int
}
