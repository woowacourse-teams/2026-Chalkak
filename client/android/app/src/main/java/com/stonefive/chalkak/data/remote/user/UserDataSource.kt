package com.stonefive.chalkak.data.remote.user

import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.user.model.SignatureUpdateResponse
import com.stonefive.chalkak.data.remote.user.model.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.user.model.UserSignatureResponse

interface UserDataSource {
    suspend fun getMySignature(): ApiResult<UserSignatureResponse>

    suspend fun createSignatureUpload(): ApiResult<SignatureUploadResponse>

    suspend fun updateSignature(signatureOriginalUploadId: String): ApiResult<SignatureUpdateResponse>

    suspend fun deleteMyAccount(): ApiResult<Unit>
}
