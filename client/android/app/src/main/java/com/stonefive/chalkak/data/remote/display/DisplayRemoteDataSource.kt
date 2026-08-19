package com.stonefive.chalkak.data.remote.display

import com.stonefive.chalkak.data.remote.display.model.DisplayResponse
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate

interface DisplayRemoteDataSource {
    suspend fun getDisplay(
        date: LocalDate?,
        sort: PostSort,
    ): DisplayResponse
}
