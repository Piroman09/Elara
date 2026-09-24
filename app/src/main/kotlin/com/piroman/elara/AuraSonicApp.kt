package com.aesthetic.spotify

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.aesthetic.spotify.data.diagnostics.StartupTiming

/**
 * Process-wide app class. Its only job is to install a shared Coil
 * [ImageLoader].
 *
 * Why this matters for smoothness: Coil's default loader has **crossfade
 * disabled**, so every cover pops in as a hard swap the instant the request
 * completes. Because covers are re-requested constantly (search results
 * changing as you type, queue rows recycling while you scroll), that read as
 * flicker. Enabling a short crossfade makes the whole app feel steadier without
 * touching a single call site — every `AsyncImage` / [TrackArtwork] picks it up.
 *
 * The caches are pinned explicitly rather than left to the defaults so the
 * behaviour is the same on a device with 2 GB of free storage and one with
 * 200 GB: covers are small and highly repetitive, so a fixed 64 MB disk cache
 * holds essentially the whole visible catalog and avoids re-downloading art
 * while scrolling.
 */
class AuraSonicApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Earliest code this app controls, so it is the only honest reference
        // point for "how long did startup take". Deliberately nothing else: this
        // runs before the first frame and must stay free.
        StartupTiming.start()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(CROSSFADE_MS)
        // Music CDNs frequently send no-cache / very short max-age headers for
        // artwork. Honouring them would re-fetch the same cover on every scroll,
        // so trust the local cache instead — cover URLs are content-addressed
        // enough (they embed the track/video id) that staleness isn't a concern.
        .respectCacheHeaders(false)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(MEMORY_CACHE_PERCENT)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .build()

    private companion object {
        const val CROSSFADE_MS = 180
        const val MEMORY_CACHE_PERCENT = 0.25
        const val DISK_CACHE_BYTES = 64L * 1024 * 1024
    }
}
