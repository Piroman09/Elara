package com.piroman.elara.data.network

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Songs-tab filter is the difference between a usable Home row and an
 * unusable one, and dropping it fails *silently*.
 *
 * Measured live 2026-09-21 against WEB_REMIX with this app's own client
 * identity, same query both ways:
 *
 *  - **with** `params` — 30 rows, every one carrying a `videoId` and a clean
 *    `Artist • Album • Duration` subtitle;
 *  - **without** — a mixed list containing artists (e.g. "Alka Yagnik" /
 *    "2.25M subscribers") with no `videoId` at all, which
 *    [YouTubeMusicApi.parseListItem] drops, plus music videos whose titles are
 *    upload packaging ("Full Video: Yaalalo Yaalalo | Irumudi | Ravi Teja | …").
 *
 * No exception is thrown in either case, so the only cheap way to catch a
 * regression is to assert on the request body itself.
 */
class YouTubeMusicSearchBodyTest {

    private fun bodyOf(json: String) = JsonParser.parseString(json).asJsonObject

    @Test
    fun `songsOnly adds the songs tab filter`() {
        val body = bodyOf(YouTubeMusicApi.searchBody("top songs this week", songsOnly = true))
        assertTrue("params missing from search body", body.has("params"))
        assertEquals(
            "EgWKAQIIAWoKEAkQBRAKEAMQBA==",
            body.get("params").asString
        )
    }

    @Test
    fun `without songsOnly there is no params key at all`() {
        val body = bodyOf(YouTubeMusicApi.searchBody("arijit singh", songsOnly = false))
        assertFalse("params should be absent, not empty", body.has("params"))
    }

    @Test
    fun `the query survives unchanged alongside the filter`() {
        val body = bodyOf(YouTubeMusicApi.searchBody("new songs", songsOnly = true))
        assertEquals("new songs", body.get("query").asString)
    }

    @Test
    fun `a query containing quotes is escaped into valid json`() {
        // The body is assembled by string concatenation, so an unescaped quote
        // would produce invalid JSON and a 400 rather than a wrong-looking list.
        val body = bodyOf(YouTubeMusicApi.searchBody("""say "hi"""", songsOnly = true))
        assertEquals("""say "hi"""", body.get("query").asString)
    }

    @Test
    fun `the web remix client identity is still the one sent`() {
        val body = bodyOf(YouTubeMusicApi.searchBody("x", songsOnly = true))
        assertEquals(
            "WEB_REMIX",
            body.getAsJsonObject("context").getAsJsonObject("client").get("clientName").asString
        )
    }
}
