package com.piroman.elara.data.network

import com.piroman.elara.data.model.SafeGson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the album-response contract against the exact failure that shipped:
 * JioSaavn renamed `songs` → `list` and flattened → nested in the same move,
 * every album on Home opened to "Couldn't load this playlist", and nothing
 * logged — an unmapped key parses as an empty list, which the repository
 * treats as "nothing here".
 *
 * The nested fixture mirrors the live `webapi.get?type=album` response of
 * 2026-09-25 (the "Vibe" album); the flat fixture mirrors the 2026-09-21
 * shape. Both must parse, the nested one wins when both appear, and an
 * unknown key shape degrades to an empty list rather than a crash.
 */
class JioWebapiAlbumResponseTest {

    private val gson = SafeGson.instance

    @Test
    fun `nested list shape parses with playable songs`() {
        val json = """
            {
              "title": "Vibe",
              "list": [
                {
                  "id": "KhQp9WJ1",
                  "title": "Raaton Mein Aana (From &quot;Vibe&quot;)",
                  "image": "https://c.saavncdn.com/994/x-150x150.jpg",
                  "more_info": {
                    "album": "Vibe",
                    "duration": "192",
                    "encrypted_media_url": "QUJDREVGRw=="
                  }
                }
              ]
            }
        """.trimIndent()

        val resp = gson.fromJson(json, JioWebapiAlbumResponse::class.java)
        assertEquals("Vibe", resp.title)
        assertEquals(1, resp.songsNested.size)
        assertTrue(resp.songsFlat.isEmpty())
        assertEquals("KhQp9WJ1", resp.songsNested[0].id)
        assertEquals(
            "encrypted url must land in more_info",
            "QUJDREVGRw==",
            resp.songsNested[0].moreInfo?.encryptedMediaUrl
        )
        assertEquals("192", resp.songsNested[0].moreInfo?.duration)
    }

    @Test
    fun `legacy flat songs shape still parses`() {
        val json = """
            {
              "title": "Vibe",
              "songs": [
                {
                  "id": "AbCdEf12",
                  "song": "Old Shape Track",
                  "duration": "201",
                  "320kbps": "1",
                  "encrypted_media_url": "WFlaR0hK",
                  "album": "Vibe",
                  "primary_artists": "Amit Trivedi"
                }
              ]
            }
        """.trimIndent()

        val resp = gson.fromJson(json, JioWebapiAlbumResponse::class.java)
        assertEquals(1, resp.songsFlat.size)
        assertTrue(resp.songsNested.isEmpty())
        assertEquals("Old Shape Track", resp.songsFlat[0].title)
        assertEquals("WFlaR0hK", resp.songsFlat[0].encryptedMediaUrl)
    }

    @Test
    fun `nested wins when both keys are present`() {
        val json = """
            {
              "title": "Vibe",
              "list": [ { "id": "n1", "title": "Nested", "more_info": { "encrypted_media_url": "QQ==" } } ],
              "songs": [ { "id": "f1", "song": "Flat", "encrypted_media_url": "Qg==" } ]
            }
        """.trimIndent()

        val resp = gson.fromJson(json, JioWebapiAlbumResponse::class.java)
        assertEquals(1, resp.songsNested.size)
        assertEquals(1, resp.songsFlat.size)
    }

    @Test
    fun `an unmapped shape degrades to empty lists, not a crash`() {
        val resp = gson.fromJson("""{"title": "Vibe"}""", JioWebapiAlbumResponse::class.java)
        assertTrue(resp.songsNested.isEmpty())
        assertTrue(resp.songsFlat.isEmpty())
        assertEquals("Vibe", resp.title)
    }
}
