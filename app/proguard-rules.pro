# Project-specific ProGuard / R8 rules for LockWeb

# Keep Room components and generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <methods>;
}

# Keep DataStore Preferences
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Keep WorkManager Worker classes instantiated by reflection
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep LockWeb Security Models & Byte Serializers
-keep class com.lockweb.app.security.PasswordHasher$HashRecord {
    public *;
}
-keep class com.lockweb.app.security.AuthResult { *; }
-keep class com.lockweb.app.security.AuthState { *; }

# Keep Android Components referenced via XML manifests
-keep class com.lockweb.app.admin.LockWebDeviceAdminReceiver { *; }
-keep class com.lockweb.app.guard.AccessibilityGuardService { *; }
-keep class com.lockweb.app.vpn.LockWebVpnService { *; }
-keep class com.lockweb.app.receiver.BootReceiver { *; }

# Kotlin Coroutines and Reflection safety
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
