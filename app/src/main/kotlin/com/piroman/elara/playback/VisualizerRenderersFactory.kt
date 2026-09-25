package com.piroman.elara.playback

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Renderers factory that builds the audio sink with [SpectrumTapper] in its
 * processor chain, so every decoded PCM frame can be observed on its way to
 * the speaker.
 *
 * Why override [buildAudioSink] rather than use `ExoPlayer.Builder`
 * configuration: Media3 1.4 exposes sink customisation only through a custom
 * `RenderersFactory` — the builder has no `setAudioSink`. Overriding just
 * this one protected method keeps every other renderer exactly as
 * `DefaultRenderersFactory` would have built it, which is what the default
 * `ExoPlayer.Builder(context)` path does internally.
 *
 * The float-output and playback-params flags are forwarded unchanged from the
 * framework's request: they encode what the rest of the pipeline negotiated
 * (e.g. `setEnableAudioTrackPlaybackParams` matters for speed changes), and
 * dropping either would silently change playback behaviour beyond adding the
 * tap. The tapper itself is pass-through — it never mutates audio.
 */
@UnstableApi
class VisualizerRenderersFactory(
    context: Context,
    private val tapper: SpectrumTapper
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink =
        DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf<AudioProcessor>(tapper))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
}
