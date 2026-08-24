package com.stonefive.chalkak.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
data object Login

@Serializable
data object Terms

@Serializable
data class Signature(val origin: SignatureOrigin)

@Serializable
@Keep
enum class SignatureOrigin {
    ONBOARDING,
    SETTINGS,
}

@Serializable
data object Today

@Serializable
data class Display(val date: String)

@Serializable
data class Feed(
    val postId: String,
    val imageUrl: String,
    val signatureUrl: String,
    val contentDescription: String,
    val title: String?,
    val likeCount: Int,
    val dateLabel: String,
    val topic: String,
    val isOwnedByCurrentUser: Boolean = false,
)

@Serializable
data object Record

@Serializable
data object Settings

@Serializable
data object PhotoUpload

@Serializable
data class PhotoUploadSuccess(
    val imageModel: String,
    val caption: String,
    val dateLabel: String,
    val topic: String,
    val nickname: String,
    val exhibitionCount: Int,
)
