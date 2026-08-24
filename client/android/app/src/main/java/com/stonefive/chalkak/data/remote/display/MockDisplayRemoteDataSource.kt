package com.stonefive.chalkak.data.remote.display

import com.stonefive.chalkak.R
import com.stonefive.chalkak.data.remote.display.model.DisplayPhotoResponse
import com.stonefive.chalkak.data.remote.display.model.DisplayResponse
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate
import kotlinx.coroutines.delay

class MockDisplayRemoteDataSource(private val responseDelayMillis: Long = 0L) : DisplayRemoteDataSource {
    override suspend fun getDisplay(
        date: LocalDate?,
        sort: PostSort,
    ): DisplayResponse {
        delay(responseDelayMillis)

        val selectedDate = (date ?: LATEST_DATE).coerceIn(EARLIEST_DATE, LATEST_DATE)
        val basePhotos = photosFor(selectedDate)
        val sortedPhotos = when (sort) {
            PostSort.LATEST -> basePhotos

            PostSort.POPULAR -> basePhotos.sortedByDescending(DisplayPhotoResponse::likeCount)

            PostSort.RANDOM -> basePhotos.sortedBy { photo ->
                photo.id.hashCode() xor selectedDate.dayOfMonth
            }
        }

        return DisplayResponse(
            selectedDate = selectedDate.toString(),
            latestDate = LATEST_DATE.toString(),
            earliestDate = EARLIEST_DATE.toString(),
            topic = if (selectedDate == LATEST_DATE) "바다" else "다리",
            photos = sortedPhotos,
            featuredPhotos = if (selectedDate < LATEST_DATE) {
                basePhotos.sortedByDescending(DisplayPhotoResponse::likeCount).take(5)
            } else {
                emptyList()
            },
        )
    }

    private fun photosFor(date: LocalDate): List<DisplayPhotoResponse> = photoSeeds.mapIndexed { index, seed ->
        seed.copy(id = "display-$date-$index")
    }

    private companion object {
        val LATEST_DATE: LocalDate = LocalDate.of(2026, 8, 5)
        val EARLIEST_DATE: LocalDate = LocalDate.of(2026, 8, 1)

        val photoSeeds = listOf(
            DisplayPhotoResponse(
                id = "seed-0",
                imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
                signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
                contentDescription = "노을과 전신주",
                title = "저녁의 선",
                likeCount = 17,
                isOwnedByCurrentUser = true,
            ),
            DisplayPhotoResponse(
                id = "seed-1",
                imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_photo}",
                signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
                contentDescription = "푸른 운동화",
                title = "한낮의 다리",
                likeCount = 31,
            ),
            DisplayPhotoResponse(
                id = "seed-2",
                imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.img_login_background}",
                signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
                contentDescription = "나무 사이로 보이는 노을",
                title = "붉은 시간",
                likeCount = 24,
            ),
            DisplayPhotoResponse(
                id = "seed-3",
                imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
                signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
                contentDescription = "구름 낀 저녁 하늘",
                title = "하루의 끝",
                likeCount = 12,
            ),
            DisplayPhotoResponse(
                id = "seed-4",
                imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_photo}",
                signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
                contentDescription = "나란히 놓인 운동화",
                title = "같은 방향",
                likeCount = 21,
            ),
            DisplayPhotoResponse(
                id = "seed-5",
                imageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.img_login_background}",
                signatureUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
                contentDescription = "늦은 오후의 가로수",
                title = "잠시 머문 빛",
                likeCount = 19,
            ),
        )
    }
}
