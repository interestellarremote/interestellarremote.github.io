package com.antigravity.remote.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private val Context.protocolDataStore by preferencesDataStore("protocol_sequences")

@Singleton
class SequenceStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    suspend fun next(deviceId: String, conversationId: String): Long = mutex.withLock {
        val key = longPreferencesKey("out_${deviceId}_$conversationId")
        val current = context.protocolDataStore.data.first()[key] ?: 0L
        // DataStore can be cleared by reinstalling the app while the paired bridge
        // still remembers the previous sequence. Epoch milliseconds preserve
        // monotonicity across reinstalls and across more than one phone.
        val result = maxOf(current + 1, System.currentTimeMillis())
        context.protocolDataStore.edit { preferences -> preferences[key] = result }
        result
    }
}
