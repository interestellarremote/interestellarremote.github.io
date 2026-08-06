package io.interestellar.remote.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val connected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(connected)
            }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        manager.registerNetworkCallback(request, callback)
        
        val active = manager.activeNetwork
        val caps = manager.getNetworkCapabilities(active)
        trySend(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

