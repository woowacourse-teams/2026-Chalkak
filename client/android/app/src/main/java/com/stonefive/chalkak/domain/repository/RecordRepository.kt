package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.RecordContent
import java.time.YearMonth

interface RecordRepository {
    suspend fun getRecord(month: YearMonth): RecordContent
}
