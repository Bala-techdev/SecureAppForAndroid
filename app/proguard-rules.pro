# SQLCipher ProGuard rules
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.zetetic.database.** { *; }

# Google Tink rules
-keep class com.google.crypto.tink.** { *; }

# Room SQLite
-keep class androidx.room.** { *; }

# Strip debug log calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
