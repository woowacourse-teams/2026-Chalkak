package com.stonefive.chalkak.data.post

import java.io.File

interface PostImageEncoder {
    suspend fun encode(
        contentUri: String,
        maxBytes: Long,
    ): PostImageEncodeResult
}

sealed interface PostImageEncodeResult {
    data class Success(val file: File) : PostImageEncodeResult

    data object UnreadableUri : PostImageEncodeResult

    data object DecodeFailed : PostImageEncodeResult

    data object EncodeFailed : PostImageEncodeResult

    data object SizeLimitExceeded : PostImageEncodeResult
}
