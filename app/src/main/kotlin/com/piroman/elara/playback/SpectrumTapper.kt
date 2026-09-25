package com.piroman.elara.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Live spectrum tap for the full-screen player's visualizer.
 *
 * Two problems, one class:
 *
 *  1. **Getting the samples.** Android's `Visualizer` attaches to an audio
 *     session by id — but we *own* the player, so the clean route is an
 *     [BaseAudioProcessor] in ExoPlayer's audio sink: every decoded PCM frame
 *     the player produces passes through [queueInput] on the playback thread.
 *     No session juggling, works on local files and streams alike. Downside:
 *     it only sees audio that goes through *our* player, which is exactly the
 *     visualizer's scope.
 *  2. **Getting it to the UI.** This runs on the playback thread at up to
 *     ~48 kHz; the UI wants a few dozen numbers at 30–60 fps. So samples are
 *     down-sampled (stride sampling), accumulated into a small ring buffer,
 *     and published as an immutable [Snapshot] through an [AtomicReference]
 *     that the UI polls once per frame. The UI is never blocked and the
 *     playback thread does no locking — just one atomic set per publish.
 *
 * **The processor's first duty is the audio itself.** A [BaseAudioProcessor]
 * owns what reaches the speaker: [queueInput] must forward the buffer into
 * the output, always — analysis is a passenger on that ride, never a
 * replacement for it. Build 18 shipped a version that only observed, and the
 * result was silence on every track.
 *
 * PCM format note: queueInput delivers 16-bit little-endian interleaved
 * samples. Stride sampling every Nth frame is fine for visuals — we need
 * loudness, not fidelity — so no per-channel deinterleaving. The published
 * frame carries two signals: band RMS magnitudes (the bars) and overall RMS
 * (a global pulse), which gives bass kicks visible weight even when their
 * energy spreads across bands.
 */
@UnstableApi
class SpectrumTapper : BaseAudioProcessor() {

    /** One immutable frame handed to the UI. Cheap to copy into draw state. */
    class Snapshot(val bands: FloatArray, val rms: Float)

    /** The latest published frame. The UI polls this; never blocked. */
    val snapshot = AtomicReference(Snapshot(FloatArray(BANDS), 0f))

    /** Whether the UI currently wants frames; off = zero-copy pass-through. */
    val active = AtomicBoolean(false)

    /** Rolling window of strided PCM samples the bands are computed over. */
    private val ring = FloatArray(RING_SIZE)
    private var ringPos = 0

    /** False when the sink negotiated a non-16-bit encoding (see [onConfigure]). */
    private var analyse = true

    /** PCM frames accumulated since the last publish (counts real frames). */
    private var pcmFrames = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Analysis assumes 16-bit little-endian frames (getShort below). The
        // sink may negotiate float or 32-bit output depending on the device —
        // on those the audio is still forwarded untouched below, but the
        // band math would read noise, so analysis is switched off instead.
        analyse = inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT
        // Pass audio through unchanged; we only observe it.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // **The speaker's data path comes first.** A BaseAudioProcessor owns
        // the audio: whatever it does not write into the output buffer never
        // reaches the sink — which is exactly the silence build 18 shipped
        // (the first version of this method only observed the input and
        // never forwarded it). Forwarding is unconditional and byte-exact.
        if (!active.get() || !analyse) {
            replaceOutputBuffer(remaining).put(inputBuffer)
            return
        }

        // Analyze a read-only duplicate while the real buffer is forwarded.
        // duplicate() shares the bytes but keeps an independent cursor, so
        // the put() below consuming `inputBuffer` does not disturb it.
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val view = inputBuffer.duplicate()
        replaceOutputBuffer(remaining).put(inputBuffer)

        var sumSquares = 0.0
        var taken = 0
        var i = view.position()
        val end = view.limit()
        // 16-bit samples every [STRIDE] frames; stride-skip both channels.
        while (i + 1 < end) {
            val sample = view.getShort(i) / 32768f
            sumSquares += sample.toDouble() * sample
            ring[ringPos] = sample
            ringPos = (ringPos + 1) % RING_SIZE
            taken++
            i += STRIDE * 2
        }
        // Each taken sample stands for STRIDE real frames of listening time.
        pcmFrames += taken * STRIDE

        if (pcmFrames >= PUBLISH_INTERVAL_FRAMES) {
            pcmFrames = 0
            val rms = if (taken > 0) Math.sqrt(sumSquares / taken).toFloat() else 0f
            publishBands(rms)
        }
    }

    override fun onFlush() {
        super.onFlush()
        // Playback config changed; the ring is stale.
        ring.fill(0f)
        ringPos = 0
        pcmFrames = 0
    }

    /** Coarse band magnitudes from the ring buffer, published to the UI. */
    private fun publishBands(rms: Float) {
        val bands = FloatArray(BANDS)
        val bandSize = RING_SIZE / BANDS
        for (b in 0 until BANDS) {
            var acc = 0.0
            for (k in 0 until bandSize) {
                val s = ring[(ringPos + b * bandSize + k) % RING_SIZE].toDouble()
                acc += s * s
            }
            // RMS per band, scaled to the same 0..255 language the UI's
            // normalisation speaks.
            bands[b] = (Math.sqrt(acc / bandSize) * 255.0).toFloat().coerceAtMost(255f)
        }
        snapshot.set(Snapshot(bands, rms))
    }

    companion object {
        /**
         * Stride: analyse every 8th PCM frame. At 48 kHz that still samples
         * the audible spectrum far above anything 24 bars can express, and it
         * keeps per-buffer work trivially cheap.
         */
        private const val STRIDE = 8

        /**
         * Window of time-domain samples analysed per published frame — 512
         * strided samples ≈ 85 ms of music at 48 kHz, long enough to hold a
         * beat, short enough to feel instant.
         */
        private const val RING_SIZE = 512

        /** Publish roughly every 1024 PCM frames ≈ 21 ms at 48 kHz (~47 fps). */
        private const val PUBLISH_INTERVAL_FRAMES = 1024

        /** Linear bands in a published snapshot. See [publishBands]. */
        const val BANDS = 64
    }
}
