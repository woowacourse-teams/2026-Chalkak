package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.TodayPostStatusResult

interface PhotoUploadEntryRepository {
    suspend fun getTodayPostStatus(): TodayPostStatusResult
}
