package com.piroman.elara.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pins the processor's first duty: **the audio must reach the speaker.**
 *
 * Build 18 shipped a [SpectrumTapper] whose `queueInput` only *observed* the
 * PCM and never wrote it into the output buffer — a `BaseAudioProcessor`
 * owns the sink's data path, so every track played silence. These tests feed
 * known bytes through the processor in each mode and assert the output is
 * byte-exact, so that failure mode can never return.
 *
 * Runs on the JVM: `BaseAudioProcessor` and `AudioProcessor.AudioFormat` are
 * plain Java with no Android framework calls, so no Robolectric is needed.
 */
class SpectrumTapperTest {

    private lateinit var tapper: SpectrumTapper

    /** A deterministic 16-bit stereo buffer — 256 frames, 1024 bytes. */
    private fun inputBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 512) {
            buffer.putShort(((i % 16) * 2047 - 16000).toShort())
        }
        buffer.flip()
        return buffer
    }

    @Before
    fun setUp() {
        tapper = SpectrumTapper()
        tapper.configure(
            AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        )
    }

    private fun assertForwarded(input: ByteBuffer) {
        val expected = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        val out = tapper.outputBufferDrained()
        assertTrue(
            "output must be byte-exact",
            out.contentEquals(expected)
        )
    }

    @Test
    fun `forwards audio byte-exactly while idle`() {
        val input = inputBuffer()
        tapper.queueInput(input)
        assertForwarded(input)
    }

    @Test
    fun `forwards audio byte-exactly while the visualizer is active`() {
        tapper.active.set(true)
        val input = inputBuffer()
        tapper.queueInput(input)
        assertForwarded(input)
    }

    @Test
    fun `forwards audio when the encoding is not 16-bit`() {
        // Float output negotiated: analysis must switch off, audio must not.
        tapper.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
        tapper.active.set(true)
        val input = inputBuffer()
        tapper.queueInput(input)
        assertForwarded(input)
    }

    @Test
    fun `active analysis publishes band magnitudes`() {
        tapper.active.set(true)
        // 1024 frames x 2 channels = 4 KB, enough to cross the publish
        // interval (1024 frames) in one buffer. The waveform's period (16
        // frames) must NOT be a multiple of the tap's stride (8) — a pattern
        // that repeats every 8 frames aliases into all-zero samples and the
        // publish legitimately sees silence.
        val input = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 2048) {
            input.putShort(((i % 16) * 2047 - 16000).toShort())
        }
        input.flip()
        tapper.queueInput(input)

        val snapshot = tapper.snapshot.get()
        assertEquals(SpectrumTapper.BANDS, snapshot.bands.size)
        // The buffer is loud, so at least one band must carry energy.
        assertTrue(snapshot.bands.any { it > 0f })
        assertTrue("rms must reflect the loud buffer", snapshot.rms > 0f)
    }

    @Test
    fun `idle passthrough never publishes`() {
        val input = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 2048) input.putShort(16000)
        input.flip()
        tapper.queueInput(input)
        // The initial snapshot is all-zero with rms 0 — nothing replaced it.
        assertEquals(0f, tapper.snapshot.get().rms, 1e-6f)
    }

    private fun AudioProcessor.outputBufferDrained(): ByteArray {
        val out = getOutput()
        val bytes = ByteArray(out.remaining())
        out.get(bytes)
        return bytes
    }
}
