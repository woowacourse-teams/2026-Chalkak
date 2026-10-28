package com.stonefive.chalkak.navigation

import com.stonefive.chalkak.domain.model.UserSessionState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ChalkakRouteTest {
    @Test
    fun `feed route preserves selected post context after serialization`() {
        val route = Feed(
            postId = "post-42",
            authorId = "author-42",
            sourceAuthorId = "author-42",
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
    fun `author posts route preserves selected author and signature`() {
        val route = AuthorPosts("author-42", "https://example.com/signature.png", false)

        assertEquals(route, roundTrip(route))
        assertEquals(AuthorPosts("my-id", null, true), roundTrip(AuthorPosts("my-id", null, true)))
    }

    @Test
    fun `older feed routes remain readable without author id`() {
        val route = Feed(
            postId = "post", originalImageUrl = "photo", thumbnailImageUrl = "thumb",
            signatureOriginalImageUrl = "sign", contentDescription = "photo", title = null,
            likeCount = 0, dateLabel = "date", topic = "topic",
        )

        assertEquals("", roundTrip(route).authorId)
    }

    @Test
    fun `feed by id route preserves calendar post context after serialization`() {
        val route = FeedById(
            postId = "post-42",
            isOwnedByCurrentUser = true,
            thumbnailImageUrl = "https://example.com/calendar-thumbnail.jpg",
        )

        assertEquals(route, roundTrip(route))
    }

    @Test
    fun `older feed by id routes remain readable without entry thumbnail`() {
        val restored = Json.decodeFromString<FeedById>(
            """{"postId":"post-42","isOwnedByCurrentUser":true}""",
        )

        assertEquals(null, restored.thumbnailImageUrl)
        assertEquals("post-42", restored.postId)
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
            date = "2025-07-18",
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

    @Test
    fun `photo upload success route preserves photo edit`() {
        val route = PhotoUploadSuccess(
            imageModel = "content://media/photo/1",
            caption = "한낮의 다리",
            date = "2025-07-18",
            topic = "다리",
            moderationStatus = "VALIDATING",
            quarterTurns = 1,
            straightenDegrees = -12.5f,
            cropCenterX = 0.4f,
            cropCenterY = 0.6f,
            cropWidth = 0.5f,
            cropHeight = 0.7f,
            filter = "Mono",
            exposure = 0.2f,
            contrast = -0.3f,
            highlights = -0.4f,
            shadows = 0.35f,
            blackPoint = 0.1f,
            saturation = -1f,
            warmth = 0.5f,
        )

        assertEquals(route, roundTrip(route))
    }

    private inline fun <reified T> roundTrip(route: T): T = Json.decodeFromString(Json.encodeToString(route))

    @Test
    fun `author posts ownership is recalculated for the signed-in member`() {
        val restored = AuthorPosts("account-a", null, isMine = true)

        assertEquals(false, restored.isMineFor(UserSessionState.Authenticated("account-b")))
        assertEquals(true, restored.isMineFor(UserSessionState.Authenticated("account-a")))
        assertEquals(
            true,
            AuthorPosts("account-b", null, isMine = false).isMineFor(UserSessionState.Authenticated("account-b")),
        )
    }

    @Test
    fun `author posts ownership falls back to the route while the session is unknown`() {
        assertEquals(true, AuthorPosts("account-a", null, isMine = true).isMineFor(UserSessionState.Loading))
    }
}
