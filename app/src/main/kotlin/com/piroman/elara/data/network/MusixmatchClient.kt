package com.aesthetic.spotify.data.network

import android.util.Base64
import android.util.Log
import com.aesthetic.spotify.data.model.LyricLine
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Musixmatch lyrics via the site's internal ws/1.1 API (reverse-engineered
 * from Strvm/musicxmatch-api):
 *
 *  1. The HMAC-SHA256 signing secret is scraped from Musixmatch's web JS bundle.
 *  2. Every request URL is signed with HMAC(secret, url + yyyyMMdd).
 *  3. A session token (token.get -> user_token) is sent as x-mxm-token.
 *  4. matcher.get resolves title+artist to a track_id, then
 *     track.richsync.get gives time-synced lines (fallback: track.lyrics.get).
 *
 * Musixmatch aggressively captcha-blocks IPs and geo-restricts endpoints, so
 * every failure path returns null and the repository falls back to LRCLIB.
 */
object MusixmatchClient {
    private const val TAG = "Musixmatch"
    private const val BASE = "https://www.musixmatch.com/ws/1.1/"
    private const val APP_ID = "web-desktop-app-v1.0"
    private const val UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36"

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile private var secret: String? = null
    @Volatile private var token: String? = null
    private val guid: String = UUID.randomUUID().toString()

    /**
     * Circuit breaker: when Musixmatch captcha-blocks or errors, skip it for
     * this long so lyrics load instantly from the fallback instead of paying
     * the timeout penalty on every track change.
     */
    @Volatile private var blockedUntilMs: Long = 0L

    class MusixmatchException(message: String) : Exception(message)

    /** Timed lyrics if richsync was available, plain lines otherwise. */
    data class Result(val lines: List<LyricLine>, val synced: Boolean)

    suspend fun getLyrics(trackTitle: String, artistName: String): Result? =
        withContext(Dispatchers.IO) {
            if (System.currentTimeMillis() < blockedUntilMs) return@withContext null
            try {
                val s = ensureSecret()
                val t = ensureToken(s)

                val trackId = matcherTrackId(trackTitle, artistName, t)
                    ?: throw MusixmatchException("track not matched")

                richsyncLines(trackId, t)?.let { return@withContext Result(it, synced = true) }

                plainLyrics(trackId, t)?.let { return@withContext Result(it, synced = false) }
                null
            } catch (e: Exception) {
                blockedUntilMs = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15)
                Log.e(TAG, "getLyrics: ${e.message} — skipping musixmatch for 15 min")
                null
            }
        }

    // ── Endpoints ────────────────────────────────────────────────────────

    private fun matcherTrackId(title: String, artist: String, token: String): Long? {
        val qs = "matcher.get?app_id=$APP_ID&format=json" +
            "&q_track=" + enc(title) +
            "&q_artist=" + enc(artist)
        val body = execute(qs, token)
        return body?.let {
            JsonParser.parseString(it)
                .asJsonObject.getAsJsonObject("message")
                .getAsJsonObject("body")?.takeIf { b -> b.has("track") }
                ?.getAsJsonObject("track")
                ?.get("track_id")?.takeIf { v -> !v.isJsonNull }?.asLong
        }
    }

    private fun richsyncLines(trackId: Long, token: String): List<LyricLine>? {
        val body = execute(
            "track.richsync.get?app_id=$APP_ID&format=json&track_id=$trackId", token
        ) ?: return null

        val root = JsonParser.parseString(body).asJsonObject
        val richsync = root.getAsJsonObject("message")
            ?.getAsJsonObject("body")?.get("richsync") ?: return null
        if (richsync.isJsonNull) return null

        // The richsync field is a JSON *string* containing the timed array.
        val arr = if (richsync.isJsonArray) richsync.asJsonArray
        else JsonParser.parseString(richsync.asString).asJsonArray

        val lines = mutableListOf<LyricLine>()
        arr.forEach { el ->
            val o = el.asJsonObject
            val ts = o.get("ts")?.asFloat ?: return@forEach
            val text = when {
                o.has("l") && o.get("l").isJsonArray ->
                    o.getAsJsonArray("l").joinToString("") { w ->
                        w.asJsonObject.get("x")?.takeIf { x -> !x.isJsonNull }?.asString ?: ""
                    }
                o.has("x") && !o.get("x").isJsonNull -> o.get("x").asString
                else -> ""
            }.trim()
            if (text.isNotEmpty()) lines += LyricLine(id = "$ts", time = ts, text = text)
        }
        return lines.ifEmpty { null }
    }

    private fun plainLyrics(trackId: Long, token: String): List<LyricLine>? {
        val body = execute(
            "track.lyrics.get?app_id=$APP_ID&format=json&track_id=$trackId", token
        ) ?: return null

        val root = JsonParser.parseString(body).asJsonObject
        val lyrics = root.getAsJsonObject("message")
            ?.getAsJsonObject("body")?.get("lyrics") as? com.google.gson.JsonObject
            ?: return null
        val text = lyrics.get("lyrics_body")?.takeIf { !it.isJsonNull }?.asString ?: return null

        // Strip the copyright footer Musixmatch appends.
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("***") }
        if (lines.isEmpty()) return null

        // No timestamps available: distribute evenly across an assumed duration.
        val step = 3f
        return lines.mapIndexed { i, line -> LyricLine(id = "$i", time = i * step, text = line) }
    }

    // ── Auth / signing ───────────────────────────────────────────────────

    @Synchronized
    private fun ensureSecret(): String {
        secret?.let { return it }

        val searchHtml = http.newCall(
            Request.Builder()
                .url("https://www.musixmatch.com/search")
                .header("User-Agent", UA)
                .header("Referer", "https://www.musixmatch.com/")
                .header("Cookie", "mxm_bab=AB")
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw MusixmatchException("search page HTTP ${resp.code}")
            resp.body!!.string()
        }

        val appJsUrl = Regex("""src="([^"]*/_next/static/chunks/pages/_app-[^"]+\.js)"""")
            .findAll(searchHtml)
            .map { it.groupValues[1] }
            .lastOrNull() ?: throw MusixmatchException("_app js not found")

        val js = http.newCall(
            Request.Builder().url(appJsUrl).header("User-Agent", UA).build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw MusixmatchException("app js HTTP ${resp.code}")
            resp.body!!.string()
        }

        val encoded = Regex("""from\(\s*"(.*?)"\s*\.split""")
            .find(js)?.groupValues?.get(1)
            ?: throw MusixmatchException("secret blob not found")

        val decoded = String(Base64.decode(encoded.reversed(), Base64.DEFAULT))
        if (decoded.isBlank()) throw MusixmatchException("secret decode failed")
        secret = decoded
        Log.d(TAG, "secret acquired (${decoded.length} chars)")
        return decoded
    }

    @Synchronized
    private fun ensureToken(s: String): String {
        token?.let { return it }
        val body = execute("token.get?app_id=$APP_ID&format=json&guid=$guid&utf8=true", null, s)
            ?: throw MusixmatchException("token.get failed")

        val root = JsonParser.parseString(body).asJsonObject
        val header = root.getAsJsonObject("message").getAsJsonObject("header")
        val hint = header.get("hint")?.takeIf { !it.isJsonNull }?.asString
        if (hint == "captcha") throw MusixmatchException("captcha blocked")

        val tok = root.getAsJsonObject("message").getAsJsonObject("body")
            ?.get("user_token")?.takeIf { !it.isJsonNull }?.asString
            ?: throw MusixmatchException("no user_token")
        token = tok
        return tok
    }

    /** Performs the signed GET; returns the body string or throws on HTTP errors. */
    private fun execute(qs: String, token: String?, s: String? = null): String? {
        val secretKey = s ?: ensureSecret()
        val url = BASE + qs
        val cal = Calendar.getInstance()
        val date = "${cal.get(Calendar.YEAR)}" +
            (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0') +
            cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(), "HmacSHA256"))
        val signature = Base64.encodeToString(
            mac.doFinal((url + date).toByteArray()),
            Base64.NO_WRAP
        )

        val builder = Request.Builder()
            .url("$url&signature=${URLEncoder.encode(signature, "UTF-8")}&signature_protocol=sha256")
            .header("User-Agent", UA)
            .header("Referer", "https://www.musixmatch.com/")
            .header("Cookie", "mxm_bab=AB")
        token?.let { builder.header("x-mxm-token", it) }

        http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw MusixmatchException("HTTP ${resp.code} for ${qs.substringBefore('?')}")
            return resp.body!!.string()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
