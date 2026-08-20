package com.stonefive.chalkak.data.remote.record

import com.stonefive.chalkak.R
import com.stonefive.chalkak.data.remote.record.model.RecordPhotoResponse
import com.stonefive.chalkak.data.remote.record.model.RecordResponse
import java.time.YearMonth
import kotlinx.coroutines.delay

class MockRecordRemoteDataSource(private val responseDelayMillis: Long = 0L) : RecordRemoteDataSource {
    override suspend fun getRecord(month: YearMonth): RecordResponse {
        delay(responseDelayMillis)

        return RecordResponse(
            month = month.toString(),
            photos = if (month == INITIAL_MONTH) {
                PHOTO_DAYS.map { day ->
                    RecordPhotoResponse(
                        date = month.atDay(day).toString(),
                        imageUrl = drawableResourceUrl(R.drawable.home_feed_photo),
                        signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                        contentDescription = "노을과 전신주",
                        title = "물결",
                    )
                }
            } else {
                emptyList()
            },
        )
    }

    private companion object {
        val INITIAL_MONTH: YearMonth = YearMonth.of(2026, 8)
        val PHOTO_DAYS = listOf(2, 3, 4, 5, 8, 9, 10, 11, 12, 15, 16, 18, 19, 21)

        fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"
    }
}
