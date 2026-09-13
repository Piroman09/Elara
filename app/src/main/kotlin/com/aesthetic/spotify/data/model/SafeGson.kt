package com.aesthetic.spotify.data.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * The one [Gson] this app should use for anything that can contain a [Track].
 *
 * Gson populates fields reflectively instead of through the constructor, so it
 * ignores Kotlin's null-safety: a [Track] whose JSON is missing a key comes
 * back holding `null` in a property declared non-null. That null then escapes
 * its declared type and crashes the first caller that trusts it —
 * `.lowercase()`, `Text(...)`, or iterating `tags`. It is what took the queue
 * screen down (`findSimilarTracks` -> `genre.lowercase()` on a restored track).
 *
 * Registering the deserializer on the Gson instance itself means **every** track
 * is normalised, at any depth — inside a queue list, a playlist, a download
 * entry — rather than each of the nine call sites having to remember to call
 * [sanitized] on the way out.
 *
 * Serialization is untouched: only deserialization is customised.
 */
object SafeGson {

    /**
     * Plain instance used to do the actual reflective parse. It deliberately has
     * no custom adapters, otherwise this deserializer would recurse forever.
     */
    private val plain = Gson()

    val instance: Gson = GsonBuilder()
        .registerTypeAdapter(
            Track::class.java,
            object : JsonDeserializer<Track> {
                override fun deserialize(
                    json: JsonElement,
                    typeOfT: Type,
                    context: JsonDeserializationContext
                ): Track = plain.fromJson(json, Track::class.java).sanitized()
            }
        )
        .create()
}
