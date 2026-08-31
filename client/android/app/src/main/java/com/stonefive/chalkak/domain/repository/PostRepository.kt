package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.PostCalendar
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostPage
import java.time.YearMonth

interface PostRepository {
    suspend fun getPostCalendar(month: YearMonth): HomeResult<PostCalendar>

    suspend fun getPostDetail(postId: String): HomeResult<PostDetail>

    suspend fun getPostContent(query: HomeQuery): HomeResult<PostContent>

    suspend fun getPostPage(query: HomeQuery): HomeResult<PostPage>

    suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): HomeResult<HomeLike>
}
