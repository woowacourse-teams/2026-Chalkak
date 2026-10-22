package com.stonefive.chalkak.data.remote.aireview

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.aireview.model.AiReviewResponse

class AiReviewDataSourceImpl(
    private val queryApi: AiReviewQueryApi,
    private val requestApi: AiReviewRequestApi,
    private val requestExecutor: ApiRequestExecutor,
) : AiReviewDataSource {
    override suspend fun getReview(postId: String): ApiResult<AiReviewResponse> =
        requestExecutor.execute { queryApi.getReview(postId) }

    override suspend fun requestReview(postId: String): ApiResult<AiReviewResponse> =
        requestExecutor.execute { requestApi.requestReview(postId) }
}
