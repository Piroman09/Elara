# ---------------------------------------------------------------------------
# ELARA — R8 keep rules
#
# The app leans on reflection in two places, and both fail *silently* when
# minified, which is why this file is more specific than it looks:
#
#   1. Gson maps JSON keys onto **field names**. A renamed or stripped field
#      does not throw — it deserialises to null / 0 and the app just quietly
#      loses data. Every Gson-touched type therefore needs `{ *; }`.
#   2. Retrofit reads its annotations and generic return types at runtime.
#      Without the attribute rules below it cannot resolve an endpoint at all.
#
# Obfuscation is disabled outright (see the bottom of this file) so that
# renaming cannot be the cause of either failure. Shrinking and optimisation
# still run, which is where the startup and frame-time wins come from.
# ---------------------------------------------------------------------------

# --- Gson: deserialised API models (fields accessed reflectively) -----------
-keep class com.piroman.elara.data.network.** { *; }
-keep class com.piroman.elara.data.model.** { *; }

# --- Gson: serialised, but living outside data.model ------------------------
# Found by auditing every `fromJson` call site. Each of these is persisted to
# disk and read back, so losing a field loses the user's data:
#   PlaylistManager -> List<Playlist>
#   DownloadManager -> List<Entry>, List<Pending>
#   PlayHistoryManager -> List<PlayStat>   (play counts, `play_stats` key)
#   ElaraBackup -> the exported library document the user re-imports
-keep class com.piroman.elara.data.playlist.Playlist { *; }
-keep class com.piroman.elara.data.download.DownloadManager$Entry { *; }
-keep class com.piroman.elara.data.download.DownloadManager$Pending { *; }
-keep class com.piroman.elara.data.history.PlayStat { *; }
-keep class com.piroman.elara.data.history.MonthAggregate { *; }
-keep class com.piroman.elara.data.backup.ElaraBackup { *; }

# --- Gson: generic type resolution -----------------------------------------
# `object : TypeToken<List<Track>>() {}.type` is resolved from the anonymous
# class's generic superclass signature. Strip the attribute and Gson can no
# longer see the element type — it hands back LinkedTreeMap instead of Track,
# and the failure surfaces as a ClassCastException far from the real cause.
-keepattributes Signature
-keepattributes InnerClasses, EnclosingMethod

# --- Retrofit --------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes *Annotation*

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-dontwarn retrofit2.**
-dontwarn javax.annotation.**

# --- Optional platform backends that are simply absent at runtime -----------
# These warnings are noise: OkHttp/Conscrypt/BouncyCastle probe for providers
# and degrade gracefully when they are missing.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Media3 / ExoPlayer ----------------------------------------------------
-dontwarn androidx.media3.**

# --- Coil ------------------------------------------------------------------
-dontwarn coil.**

# --- Kotlin coroutines internals -------------------------------------------
-dontwarn kotlinx.coroutines.**

# --- Do not rename anything ------------------------------------------------
# Optimisation and shrinking stay on; only the renaming pass is skipped. That
# costs a little APK size and buys immunity to the whole class of
# reflection-by-name breakage described at the top of this file.
-dontobfuscate
