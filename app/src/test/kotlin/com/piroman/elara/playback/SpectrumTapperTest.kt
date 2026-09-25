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
 * Two real bugs this class shipped, both invisible to logic-only tests:
 *
 *  1. Build 18: `queueInput` only *observed* the PCM and never wrote it to
 *     the output buffer — unforwarded audio dies in the processor.
 *  2. Build 19: forwarded the bytes but never **flipped** the output buffer.
 *     `replaceOutputBuffer` hands out a write-mode buffer whose position sits
 *     at the data end; the sink drains position→limit, so an unflipped
 *     buffer reads back as zero bytes and playback stalls in "buffering"
 *     forever.
 *
 * So these tests assert the **count** of bytes first (a 0-byte pass would
 * have caught bug 2) and then their exact content (bug 1). The input is
 * snapshotted *before* queueing, because `queueInput` consumes it.
 *
 * Runs on the JVM: `BaseAudioProcessor` and `AudioProcessor.AudioFormat` are
 * plain Java with no Android framework calls, so no Robolectric is needed.
 */
class SpectrumTapperTest {

    private lateinit var tapper: SpectrumTapper

    /** A deterministic 16-bit buffer — 512 samples, 1024 bytes. */
    private fun inputBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 512) {
            buffer.putShort(((i % 16) * 2047 - 16000).toShort())
        }
        buffer.flip()
        return buffer
    }

    /** Queues [input] and asserts the drained output is byte-exact with it. */
    private fun assertQueuedForward(input: ByteBuffer) {
        val expected = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        assertTrue("test data must not be empty", expected.isNotEmpty())
        tapper.queueInput(input)

        val out = tapper.getOutput()
        assertEquals(
            "the sink must receive every byte back (a 0-byte drain means the " +
                "output buffer was never flipped)",
            expected.size.toLong(),
            out.remaining().toLong()
        )
        val actual = ByteArray(out.remaining()).also { out.get(it) }
        assertTrue(
            "output must be byte-exact with the input",
            actual.contentEquals(expected)
        )
        assertEquals("output must be fully drained", 0, out.remaining())
    }

    @Before
    fun setUp() {
        tapper = SpectrumTapper()
        tapper.configure(
            AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        )
    }

    @Test
    fun `forwards audio byte-exactly while idle`() {
        assertQueuedForward(inputBuffer())
    }

    @Test
    fun `forwards audio byte-exactly while the visualizer is active`() {
        tapper.active.set(true)
        assertQueuedForward(inputBuffer())
    }

    @Test
    fun `forwards audio when the encoding is not 16-bit`() {
        // Float output negotiated: analysis must switch off, audio must not.
        tapper.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
        tapper.active.set(true)
        assertQueuedForward(inputBuffer())
    }

    @Test
    fun `consecutive buffers all forward`() {
        // The buffer is reused across queueInput calls (replaceOutputBuffer
        // clears it); a one-shot pass can hide a state bug that stalls the
        // second buffer — and playback is nothing BUT consecutive buffers.
        tapper.active.set(true)
        repeat(4) { assertQueuedForward(inputBuffer()) }
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

        // Audio must still flow while analysis runs — a publish must never
        // cost the speaker its bytes.
        assertTrue("audio must still be forwarded while active", tapper.getOutput().remaining() > 0)
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
}
