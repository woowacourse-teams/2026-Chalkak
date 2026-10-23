package com.stonefive.chalkak.data.remote.aireview

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.aireview.model.AiReviewResponse

interface AiReviewDataSource {
    suspend fun getReview(postId: String): ApiResult<AiReviewResponse>

    suspend fun requestReview(postId: String): ApiResult<AiReviewResponse>
}
