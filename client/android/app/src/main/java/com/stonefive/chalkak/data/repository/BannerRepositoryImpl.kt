package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.banner.BannerApi
import com.stonefive.chalkak.domain.model.Banner
import com.stonefive.chalkak.domain.model.BannerPage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.repository.BannerRepository

class BannerRepositoryImpl(
    private val api: BannerApi,
    private val requestExecutor: ApiRequestExecutor,
) : BannerRepository {
    override suspend fun getBanners(
        page: Int,
        pageSize: Int,
    ): HomeResult<BannerPage> {
        require(page >= 1 && pageSize >= 1)
        return when (val result = requestExecutor.execute { api.getBanners(page, pageSize) }) {
            is ApiResult.Success -> HomeResult.Success(
                BannerPage(
                    currentPage = result.value.currentPage,
                    pageSize = result.value.pageSize,
                    hasNext = result.value.hasNext,
                    banners = result.value.banners.map {
                        Banner(it.id, it.title, it.subtitle, it.webViewUrl, it.thumbnailUrl, it.createdAt)
                    },
                ),
            )

            is ApiResult.Failure -> HomeResult.Failure(
                when (val error = result.error) {
                    ApiError.Network -> HomeFailure.Network
                    ApiError.InvalidResponse -> HomeFailure.InvalidResponse
                    is ApiError.Http -> HomeFailure.Http(error.statusCode)
                },
            )
        }
    }
}
