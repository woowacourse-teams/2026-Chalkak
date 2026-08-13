package com.stonefive.chalkak

import com.stonefive.chalkak.data.remote.home.MockHomeRemoteDataSource
import com.stonefive.chalkak.data.repository.HomeRepositoryImpl
import com.stonefive.chalkak.domain.repository.HomeRepository

class AppContainer {
    val homeRepository: HomeRepository by lazy {
        HomeRepositoryImpl(
            remoteDataSource = MockHomeRemoteDataSource(),
        )
    }
}
