package com.stonefive.chalkak.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ChalkakRouteTest {
    @Test
    fun `feed route preserves selected post context after serialization`() {
        val route = Feed(
            postId = "post-42",
            originalImageUrl = "https://example.com/photo.jpg",
            thumbnailImageUrl = "https://example.com/photo-thumbnail.jpg",
            signatureOriginalImageUrl = "https://example.com/signature.png",
            signatureThumbnailImageUrl = "https://example.com/signature-thumbnail.png",
            contentDescription = "sunset",
            title = null,
            likeCount = 24,
            dateLabel = "8월 5일의 주제",
            topic = "바다",
        )

        val restored = Json.decodeFromString<Feed>(Json.encodeToString(route))

        assertEquals(route, restored)
    }

    @Test
    fun `feed by id route preserves calendar post context after serialization`() {
        val route = FeedById(
            postId = "post-42",
            isOwnedByCurrentUser = true,
        )

        assertEquals(route, roundTrip(route))
    }

    @Test
    fun `display route preserves selected date after serialization`() {
        val route = Display(date = "2026-08-02")

        val restored = Json.decodeFromString<Display>(Json.encodeToString(route))

        assertEquals(route, restored)
    }

    @Test
    fun `signature flow destinations are serializable`() {
        assertEquals(OnboardingSignature, roundTrip(OnboardingSignature))
        assertEquals(ChangeSignature, roundTrip(ChangeSignature))
        assertEquals(OnboardingSignaturePreview, roundTrip(OnboardingSignaturePreview))
        assertEquals(ChangeSignaturePreview, roundTrip(ChangeSignaturePreview))
    }

    @Test
    fun `photo upload success route preserves submission context after serialization`() {
        val route = PhotoUploadSuccess(
            imageModel = "content://media/photo/1",
            caption = "한낮의 다리",
            dateLabel = "2025. 07. 18",
            topic = "다리",
            nickname = "@@",
            exhibitionCount = 128,
        )

        val restored = Json.decodeFromString<PhotoUploadSuccess>(Json.encodeToString(route))

        assertEquals(route, restored)
    }

    private inline fun <reified T> roundTrip(route: T): T = Json.decodeFromString(Json.encodeToString(route))
}
