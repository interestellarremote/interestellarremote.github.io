-keepattributes Signature,*Annotation*
-keep class io.interestellar.remote.data.** { *; }

# Firebase Realtime Database
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# GSON / JSON
-keep class org.json.** { *; }

