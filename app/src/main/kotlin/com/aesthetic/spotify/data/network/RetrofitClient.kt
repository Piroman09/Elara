package com.aesthetic.spotify.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val MUSIC_BASE_URL = "https://itunes.apple.com/"
    private const val LYRICS_BASE_URL = "https://lrclib.net/"
    private const val SOUNDCLOUD_BASE_URL = "https://api-v2.soundcloud.com/"
    private const val JIOSAAVN_BASE_URL = "https://www.jiosaavn.com/"

    /** Some providers (lrclib.net) CDN-block default library user agents. */
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) Elara/1.0"

    private fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
            )
        }
        .build()

    private fun build(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttp())
        .build()

    val musicApi: MusicApi by lazy { build(MUSIC_BASE_URL).create(MusicApi::class.java) }
    val lyricsApi: LyricsApi by lazy { build(LYRICS_BASE_URL).create(LyricsApi::class.java) }
    val soundCloudApi: SoundCloudApi by lazy { build(SOUNDCLOUD_BASE_URL).create(SoundCloudApi::class.java) }
    val jioSaavnApi: JioSaavnApi by lazy { build(JIOSAAVN_BASE_URL).create(JioSaavnApi::class.java) }
}
