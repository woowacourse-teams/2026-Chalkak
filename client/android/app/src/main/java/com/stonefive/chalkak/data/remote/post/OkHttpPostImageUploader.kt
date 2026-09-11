package com.stonefive.chalkak.data.remote.post

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

class OkHttpPostImageUploader(private val client: OkHttpClient) : PostImageUploader {
    override suspend fun upload(
        uploadUrl: String,
        contentType: String,
        imageFile: File,
    ): PostImageUploadResult = withContext(Dispatchers.IO) {
        try {
            val mediaType = contentType.toMediaType()
            val request = Request
                .Builder()
                .url(uploadUrl)
                .put(imageFile.asRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    PostImageUploadResult.Success
                } else {
                    PostImageUploadResult.Rejected
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            PostImageUploadResult.InvalidUploadRequest
        } catch (_: IOException) {
            PostImageUploadResult.NetworkFailure
        }
    }
}
