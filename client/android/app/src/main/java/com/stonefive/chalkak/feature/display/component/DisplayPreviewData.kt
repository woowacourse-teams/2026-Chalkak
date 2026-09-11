package com.stonefive.chalkak.feature.display.component

import com.stonefive.chalkak.R
import com.stonefive.chalkak.domain.model.Post

val previewDisplayPost = Post(
    id = "preview",
    originalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_photo}",
    thumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_photo}",
    signatureOriginalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
    signatureThumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
    contentDescription = "푸른 운동화",
    title = "한낮의 다리",
    likeCount = 17,
)

val previewDisplayPhotos = listOf(
    previewDisplayPost,
    previewDisplayPost.copy(
        id = "preview-2",
        originalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
        thumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.home_feed_photo}",
        signatureOriginalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
        signatureThumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.preview_signature}",
        contentDescription = "노을과 전신주",
        title = "저녁의 선",
        likeCount = 31,
    ),
    previewDisplayPost.copy(
        id = "preview-3",
        originalImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.img_login_background}",
        thumbnailImageUrl = "android.resource://com.stonefive.chalkak/${R.drawable.img_login_background}",
        contentDescription = "나무 사이로 보이는 노을",
        title = "붉은 시간",
        likeCount = 24,
    ),
)
