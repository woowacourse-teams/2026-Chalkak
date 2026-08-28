package com.stonefive.chalkak.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Login

@Serializable
data object Terms

@Serializable
data object OnboardingSignature

@Serializable
data object ChangeSignature

@Serializable
data object OnboardingSignaturePreview

@Serializable
data object ChangeSignaturePreview

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
