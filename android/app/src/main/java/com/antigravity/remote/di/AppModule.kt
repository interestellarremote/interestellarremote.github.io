package com.antigravity.remote.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.antigravity.remote.data.LocalDatabase
import com.antigravity.remote.data.RemoteDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `deviceId` TEXT NOT NULL, `projectId` TEXT NOT NULL, `prompt` TEXT NOT NULL, `status` TEXT NOT NULL, `phase` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `elapsedSeconds` INTEGER NOT NULL, `error` TEXT, `retryOf` TEXT, `unread` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `audit_log` (`id` TEXT NOT NULL, `taskId` TEXT, `conversationId` TEXT, `kind` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
        }
    }

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `processed_events` (`id` TEXT NOT NULL, `processedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
        }
    }

    @Provides @Singleton fun database(@ApplicationContext context: Context): LocalDatabase =
        Room.databaseBuilder(context, LocalDatabase::class.java, "antigravity-remote.db")
            .addMigrations(migration1To2, migration2To3)
            .build()
    @Provides fun dao(database: LocalDatabase): RemoteDao = database.dao()
    @Provides @Singleton fun auth(): FirebaseAuth = FirebaseAuth.getInstance()
    @Provides @Singleton fun realtime(): FirebaseDatabase = FirebaseDatabase.getInstance()
    @Provides @Singleton fun functions(): FirebaseFunctions = FirebaseFunctions.getInstance()
    @Provides @Singleton fun storage(): FirebaseStorage = FirebaseStorage.getInstance()
}
