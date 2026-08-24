package com.stonefive.chalkak.data.remote.record

import com.stonefive.chalkak.data.remote.record.model.RecordResponse
import java.time.YearMonth

interface RecordRemoteDataSource {
    suspend fun getRecord(month: YearMonth): RecordResponse
}
