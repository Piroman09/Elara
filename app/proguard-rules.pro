# Keep Gson-deserialized API models (fields accessed reflectively).
-keep class com.aesthetic.spotify.data.network.** { *; }
-keep class com.aesthetic.spotify.data.model.** { *; }

# Media3 / ExoPlayer
-dontwarn androidx.media3.**
