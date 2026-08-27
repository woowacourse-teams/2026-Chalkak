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
        } catch (_: IOException) {
            SignatureUploadResult.NetworkFailure
        }
    }

    private companion object {
        val PNG_MEDIA_TYPE = "image/png".toMediaType()
    }
}
