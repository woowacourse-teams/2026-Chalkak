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
            availableMonths = listOf(INITIAL_MONTH.toString()),
            photos = if (month == INITIAL_MONTH) {
                PHOTO_DAYS.map { day ->
                    val isLandscapePhoto = day == LANDSCAPE_PHOTO_DAY
                    RecordPhotoResponse(
                        date = month.atDay(day).toString(),
                        imageUrl = picsumPhotoUrl(month, day, isLandscapePhoto),
                        signatureUrl = drawableResourceUrl(R.drawable.preview_signature),
                        contentDescription = "인터넷 Mock 사진",
                        title = if (isLandscapePhoto) "하루" else "사진 $day",
                    )
                }
            } else {
                emptyList()
            },
        )
    }

    private companion object {
        val INITIAL_MONTH: YearMonth = YearMonth.of(2026, 8)
        val PHOTO_DAYS = (1..31).filter { it % 4 != 0 }
        const val LANDSCAPE_PHOTO_DAY = 21
        const val PHOTO_URL = "https://picsum.photos/seed/chalkak"

        fun drawableResourceUrl(resourceId: Int): String = "android.resource://com.stonefive.chalkak/$resourceId"

        fun picsumPhotoUrl(
            month: YearMonth,
            day: Int,
            isLandscapePhoto: Boolean,
        ): String {
            val dimensions = if (isLandscapePhoto) "800/600" else "600/800"
            return "$PHOTO_URL-$month-${day.toString().padStart(2, '0')}/$dimensions.jpg"
        }
    }
}
