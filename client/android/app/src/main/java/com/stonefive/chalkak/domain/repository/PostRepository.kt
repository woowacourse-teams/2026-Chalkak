package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostPage

interface PostRepository {
    suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent>

    suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage>

    suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike>
}
