package com.stonefive.chalkak.data.remote.user

import com.stonefive.chalkak.data.remote.ApiRequestExecutor
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.user.model.UserSignatureResponse

class UserDataSourceImpl(
    private val api: UserApi,
    private val requestExecutor: ApiRequestExecutor,
) : UserDataSource {
    override suspend fun getMySignature(): ApiResult<UserSignatureResponse> = requestExecutor.execute {
        api.getMySignature()
    }
}
