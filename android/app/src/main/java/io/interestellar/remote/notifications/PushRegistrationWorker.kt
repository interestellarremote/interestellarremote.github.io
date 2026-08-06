package io.interestellar.remote.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class PushRegistrationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        return runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            val key = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
            FirebaseDatabase.getInstance().getReference("users/$uid/fcmTokens/$key").setValue(token).await()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

