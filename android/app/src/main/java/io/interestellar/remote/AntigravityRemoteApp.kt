package io.interestellar.remote

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.interestellar.remote.notifications.PushRegistrationWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AntigravityRemoteApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "refresh-fcm-registration",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PushRegistrationWorker>(24, TimeUnit.HOURS).build(),
        )
    }
}

