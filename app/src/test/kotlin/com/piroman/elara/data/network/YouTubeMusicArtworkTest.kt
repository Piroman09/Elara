package com.piroman.elara.data.network

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cover art is easy to get silently wrong: the renderer nests the image two
 * levels down, under `thumbnail.musicThumbnailRenderer.thumbnail.thumbnails[]`.
 * Reading the plausible-looking `thumbnail.thumbnails[]` returns null for every
 * single row without ever throwing, which is exactly how the app shipped with
 * blank covers on every YouTube Music track.
 *
 * The fixtures below are trimmed from a real WEB_REMIX `search` response.
 */
class YouTubeMusicArtworkTest {

    private fun row(thumbnailJson: String, subtitle: String = "Song • 5:52") =
        JsonParser.parseString(
            """
            {
              "playlistItemData": { "videoId": "q1uPPBJ2tcI" },
              "flexColumns": [
                {
                  "musicResponsiveListItemFlexColumnRenderer": {
                    "text": { "runs": [ { "text": "Phir Bhi Tumko Chaahunga" } ] }
                  }
                },
                {
                  "musicResponsiveListItemFlexColumnRenderer": {
                    "text": { "runs": [ { "text": "$subtitle" } ] }
                  }
                }
              ],
              "thumbnail": $thumbnailJson
            }
            """.trimIndent()
        ).asJsonObject

    /** Exactly the shape and sizing suffix the live endpoint returns. */
    private val realThumbnail = """
        {
          "musicThumbnailRenderer": {
            "thumbnail": {
              "thumbnails": [
                { "url": "https://yt3.googleusercontent.com/ABC=w60-h60-l90-rj", "width": 60, "height": 60 },
                { "url": "https://yt3.googleusercontent.com/ABC=w120-h120-l90-rj", "width": 120, "height": 120 }
              ]
            },
            "thumbnailCrop": "MUSIC_THUMBNAIL_CROP_UNSPECIFIED"
          }
        }
    """.trimIndent()

    @Test
    fun `reads the nested musicThumbnailRenderer path`() {
        val track = YouTubeMusicApi.parseListItem(row(realThumbnail))
        assertEquals(
            "https://yt3.googleusercontent.com/ABC=w544-h544-l90-rj",
            track?.albumArt
        )
    }

    @Test
    fun `picks the widest thumbnail on offer, not the first`() {
        // Both entries upscale to the same string, so assert on the source width
        // by giving the two candidates distinguishable URLs.
        val thumbnail = """
            {
              "musicThumbnailRenderer": {
                "thumbnail": {
                  "thumbnails": [
                    { "url": "https://example.test/small=w60-h60", "width": 60, "height": 60 },
                    { "url": "https://example.test/large=w226-h226", "width": 226, "height": 226 }
                  ]
                }
              }
            }
        """.trimIndent()
        assertEquals(
            "https://example.test/large=w544-h544",
            YouTubeMusicApi.parseListItem(row(thumbnail))?.albumArt
        )
    }

    @Test
    fun `falls back to the video still when the row carries no thumbnail`() {
        assertEquals(
            "https://i.ytimg.com/vi/q1uPPBJ2tcI/hqdefault.jpg",
            YouTubeMusicApi.parseListItem(row("{}"))?.albumArt
        )
    }

    @Test
    fun `falls back to the video still when the thumbnail list is empty`() {
        val empty = """{ "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [] } } }"""
        assertEquals(
            "https://i.ytimg.com/vi/q1uPPBJ2tcI/hqdefault.jpg",
            YouTubeMusicApi.parseListItem(row(empty))?.albumArt
        )
    }

    @Test
    fun `leaves a url without a sizing suffix alone`() {
        val plain = """
            {
              "musicThumbnailRenderer": {
                "thumbnail": {
                  "thumbnails": [ { "url": "https://example.test/cover.jpg", "width": 500, "height": 500 } ]
                }
              }
            }
        """.trimIndent()
        assertEquals(
            "https://example.test/cover.jpg",
            YouTubeMusicApi.parseListItem(row(plain))?.albumArt
        )
    }

    @Test
    fun `makes protocol relative artwork absolute`() {
        val relative = """
            {
              "musicThumbnailRenderer": {
                "thumbnail": {
                  "thumbnails": [ { "url": "//lh3.example.test/cover=w60-h60-l90", "width": 60, "height": 60 } ]
                }
              }
            }
        """.trimIndent()
        assertEquals(
            "https://lh3.example.test/cover=w544-h544-l90",
            YouTubeMusicApi.parseListItem(row(relative))?.albumArt
        )
    }

    // ── Subtitle parsing (same function, same fixture) ────────────────────

    @Test
    fun `a row with no artist does not report the duration as the artist`() {
        val track = YouTubeMusicApi.parseListItem(row(realThumbnail, subtitle = "Song • 5:52"))
        assertEquals("Unknown Artist", track?.artist)
        assertEquals(352, track?.duration)
        assertEquals("YouTube Music", track?.album)
    }

    @Test
    fun `splits artist and album out of the subtitle`() {
        val track = YouTubeMusicApi.parseListItem(
            row(realThumbnail, subtitle = "Arijit Singh • Half Girlfriend • 5:52")
        )
        assertEquals("Arijit Singh", track?.artist)
        assertEquals("Half Girlfriend", track?.album)
        assertEquals(352, track?.duration)
    }

    @Test
    fun `a video row keeps the channel and drops the view count`() {
        val track = YouTubeMusicApi.parseListItem(
            row(realThumbnail, subtitle = "Video • ABT Lofi Music • 69M views • 3:47")
        )
        assertEquals("ABT Lofi Music", track?.artist)
        assertEquals("YouTube Music", track?.album)
        assertEquals(227, track?.duration)
    }
}
