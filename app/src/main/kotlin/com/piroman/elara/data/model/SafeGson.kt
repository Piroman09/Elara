package com.piroman.elara.data.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
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

/**
 * The `Type` for a persisted `List<T>`, built from `Class` literals.
 *
 * **Never use `object : TypeToken<List<T>>() {}.type` for anything the app writes to disk.**
 * That pattern resolves `T` from the anonymous subclass's *generic superclass signature*,
 * and R8 does not reliably preserve those. This is not hypothetical — it is what emptied
 * the user's library on 2026-09-21:
 *
 * ```
 * playlists: LOAD FAILED (41528B) TypeToken must be created with a type argument:
 *   new TypeToken<...>() {}; When using code shrinkers ... generic signatures are preserved.
 *   — library reads as EMPTY
 * ```
 *
 * Inspecting the built release dex afterwards showed why: the eleven anonymous `TypeToken`
 * subclasses had been merged down to a single class carrying a single `Signature`
 * attribute, so `getGenericSuperclass()` returned the raw type and Gson threw. Each store
 * then read as empty **and the next save overwrote the user's real data** — the file was
 * still 41 KB on disk while the app reported an empty library. `-keepattributes Signature`
 * was already in `proguard-rules.pro` and did not prevent it.
 *
 * It is a **release-only** failure, so no JVM unit test can reproduce it. The defence is
 * twofold: this function, and a check in `verifyReleaseDex` that fails the build if the
 * anonymous pattern reappears anywhere in the main sources.
 *
 * [TypeToken.getParameterized] builds the `ParameterizedType` from `Class` objects at
 * runtime, so there is no signature to strip and R8 cannot break it.
 */
internal fun listTypeOf(element: Class<*>): Type =
    TypeToken.getParameterized(List::class.java, element).type
