package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.BannerPage
import com.stonefive.chalkak.domain.model.HomeResult

interface BannerRepository {
    suspend fun getBanners(
        page: Int = 1,
        pageSize: Int = 1,
    ): HomeResult<BannerPage>
}
