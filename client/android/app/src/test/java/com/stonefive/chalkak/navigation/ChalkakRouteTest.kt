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
            imageUrl = "https://example.com/photo.jpg",
            signatureUrl = "https://example.com/signature.png",
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
    fun `display route preserves selected date after serialization`() {
        val route = Display(date = "2026-08-02")

        val restored = Json.decodeFromString<Display>(Json.encodeToString(route))

        assertEquals(route, restored)
    }

    @Test
    fun `photo upload route preserves the topic date captured on entry`() {
        val route = PhotoUpload(topicDate = "2026-08-29")

        val restored = roundTrip(route)

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
    fun `photo upload success route preserves submission context without mock fields`() {
        val route = PhotoUploadSuccess(
            imageModel = "content://media/photo/1",
            caption = "한낮의 다리",
            dateLabel = "2025. 07. 18",
            topic = "다리",
            moderationStatus = "VALIDATING",
        )

        val encoded = Json.encodeToString(route)
        val restored = Json.decodeFromString<PhotoUploadSuccess>(encoded)

        assertEquals(route, restored)
        org.junit.Assert
            .assertFalse(encoded.contains("nickname"))
        org.junit.Assert
            .assertFalse(encoded.contains("exhibitionCount"))
    }

    private inline fun <reified T> roundTrip(route: T): T = Json.decodeFromString(Json.encodeToString(route))
}
