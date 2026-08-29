package com.stonefive.chalkak.data.remote.signature

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpPresignedImageUploader(private val client: OkHttpClient) : PresignedImageUploader {
    override suspend fun upload(
        uploadUrl: String,
        contentType: String,
        content: UploadContent,
    ): PresignedUploadResult = withContext(Dispatchers.IO) {
        try {
            val mediaType = contentType.toMediaType()
            val requestBody = when (content) {
                is UploadContent.Bytes -> content.value.toRequestBody(mediaType)
                is UploadContent.FileContent -> content.file.asRequestBody(mediaType)
            }
            val request = Request
                .Builder()
                .url(uploadUrl)
                .put(requestBody)
                .header(CONTENT_TYPE_HEADER, contentType)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    PresignedUploadResult.Success
                } else {
                    PresignedUploadResult.Rejected
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            PresignedUploadResult.InvalidUploadUrl
        } catch (_: IOException) {
            PresignedUploadResult.NetworkFailure
        }
    }

    private companion object {
        const val CONTENT_TYPE_HEADER = "Content-Type"
    }
}
