package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.signature.SignatureRemoteDataSource
import com.stonefive.chalkak.domain.repository.SignatureRepository

class SignatureRepositoryImpl(private val remoteDataSource: SignatureRemoteDataSource) : SignatureRepository {
    override suspend fun uploadSignature(signaturePng: ByteArray) {
        remoteDataSource.uploadSignature(signaturePng)
    }
}
