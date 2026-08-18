package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.DisplayContent
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate

interface DisplayRepository {
    suspend fun getDisplay(
        date: LocalDate?,
        sort: PostSort,
    ): DisplayContent
}
