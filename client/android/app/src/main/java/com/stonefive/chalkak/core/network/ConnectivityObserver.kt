package com.stonefive.chalkak.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface ConnectivityObserver {
    val status: Flow<ConnectivityStatus>
}

enum class ConnectivityStatus {
    Online,
    Offline,
}

class AndroidConnectivityObserver(context: Context) : ConnectivityObserver {
    private val connectivityManager = requireNotNull(
        context.applicationContext.getSystemService(ConnectivityManager::class.java),
    )

    override val status: Flow<ConnectivityStatus> = callbackFlow {
        fun emitCurrentStatus() {
            trySend(currentStatus())
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                emitCurrentStatus()
            }

            override fun onLost(network: Network) {
                emitCurrentStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                emitCurrentStatus()
            }
        }

        emitCurrentStatus()
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun currentStatus(): ConnectivityStatus {
        val activeNetwork = connectivityManager.activeNetwork ?: return ConnectivityStatus.Offline
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return ConnectivityStatus.Offline

        return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            ConnectivityStatus.Online
        } else {
            ConnectivityStatus.Offline
        }
    }
}
