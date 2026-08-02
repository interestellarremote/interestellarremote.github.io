package com.antigravity.remote.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.antigravity.remote.MainActivity
import com.antigravity.remote.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.antigravity.remote.data.RemoteRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RemoteMessagingService : FirebaseMessagingService() {
    @Inject lateinit var repository: RemoteRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { repository.registerPushToken(token) }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        message.data["deviceId"]?.let { deviceId ->
            val syncRequest = OneTimeWorkRequestBuilder<EventSyncWorker>()
                .setInputData(workDataOf(EventSyncWorker.KEY_DEVICE_ID to deviceId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(this).enqueueUniqueWork(
                "sync-events-$deviceId",
                ExistingWorkPolicy.REPLACE,
                syncRequest,
            )
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("remote_events", "Eventos remotos", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val kind = message.data["kind"]
        val title = when (kind) { "approval" -> "Aprovação necessária"; "error" -> "Tarefa com erro"; else -> "Tarefa concluída" }
        manager.notify(
            (System.currentTimeMillis() and 0x7fffffff).toInt(),
            NotificationCompat.Builder(this, "remote_events")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText("Abra o Interestellar Remote para ver os detalhes cifrados.")
                .setContentIntent(pending).setAutoCancel(true).build()
        )
    }
}
