package io.interestellar.remote.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("remote_session", Context.MODE_PRIVATE)

    var deviceId: String?
        get() = preferences.getString("device_id", null)
        set(value) = preferences.edit().putString("device_id", value).apply()

    var projectId: String?
        get() = preferences.getString("project_id", null)
        set(value) = preferences.edit().putString("project_id", value).apply()

    var conversationId: String?
        get() = preferences.getString("conversation_id", null)
        set(value) = preferences.edit().putString("conversation_id", value).apply()

    var model: String?
        get() = preferences.getString("model", null)
        set(value) = preferences.edit().putString("model", value).apply()

    var dailyMessageDate: String?
        get() = preferences.getString("daily_message_date", null)
        set(value) = preferences.edit().putString("daily_message_date", value).apply()

    var dailyMessageCount: Int
        get() = preferences.getInt("daily_message_count", 0)
        set(value) = preferences.edit().putInt("daily_message_count", value).apply()

    var devForcePro: Boolean
        get() = preferences.getBoolean("dev_force_pro", false)
        set(value) = preferences.edit().putBoolean("dev_force_pro", value).apply()

    fun clear() = preferences.edit().clear().apply()
}

