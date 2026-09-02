package com.stonefive.chalkak.data.remote.user

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.user.model.SignatureUpdateRequest
import com.stonefive.chalkak.data.remote.user.model.SignatureUpdateResponse
import com.stonefive.chalkak.data.remote.user.model.SignatureUploadResponse
import com.stonefive.chalkak.data.remote.user.model.UserSignatureResponse

class UserDataSourceImpl(
    private val api: UserApi,
    private val requestExecutor: ApiRequestExecutor,
) : UserDataSource {
    override suspend fun getMySignature(): ApiResult<UserSignatureResponse> = requestExecutor.execute {
        api.getMySignature()
    }

    override suspend fun createSignatureUpload(): ApiResult<SignatureUploadResponse> = requestExecutor.execute {
        api.createSignatureUpload()
    }

    override suspend fun updateSignature(signatureOriginalUploadId: String): ApiResult<SignatureUpdateResponse> =
        requestExecutor.execute {
            api.updateSignature(
                SignatureUpdateRequest(
                    signatureOriginalUploadId = signatureOriginalUploadId,
                ),
            )
        }

    override suspend fun deleteMyAccount(): ApiResult<Unit> = requestExecutor.executeNoContent {
        api.deleteMyAccount()
    }
}
