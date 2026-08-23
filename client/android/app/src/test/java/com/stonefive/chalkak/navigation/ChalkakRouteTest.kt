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
    fun `signature route preserves its entry origin after serialization`() {
        SignatureOrigin.entries.forEach { origin ->
            val route = Signature(origin)

            val restored = Json.decodeFromString<Signature>(Json.encodeToString(route))

            assertEquals(route, restored)
        }
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
}
