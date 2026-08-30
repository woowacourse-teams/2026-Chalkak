package com.stonefive.chalkak.data.remote.signature

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpSignatureUploader(private val client: OkHttpClient) : SignatureUploader {
    override suspend fun upload(
        uploadUrl: String,
        signaturePng: ByteArray,
    ): SignatureUploadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request
                .Builder()
                .url(uploadUrl)
                .put(signaturePng.toRequestBody(PNG_MEDIA_TYPE))
                .header(CONTENT_TYPE_HEADER, PNG_CONTENT_TYPE)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    SignatureUploadResult.Success
                } else {
                    SignatureUploadResult.Rejected
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            SignatureUploadResult.InvalidUploadUrl
        } catch (_: IOException) {
            SignatureUploadResult.NetworkFailure
        }
    }

    private companion object {
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val PNG_CONTENT_TYPE = "image/png"
        val PNG_MEDIA_TYPE = PNG_CONTENT_TYPE.toMediaType()
    }
}
