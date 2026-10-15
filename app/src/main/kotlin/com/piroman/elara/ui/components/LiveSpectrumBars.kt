package com.piroman.elara.ui.components

import android.provider.Settings
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.piroman.elara.playback.SpectrumAnalyzer
import com.piroman.elara.playback.SpectrumTapper

/**
 * True when the user has switched system animations off (animator duration
 * scale 0). Shared by every ambient animation — an infinite frame loop is
 * exactly what that setting exists to stop.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/**
 * Live spectrum bars for the full-screen player, driven by [tapper].
 *
 * Architecture follows the same rule as [EqualizerBars]: the **draw phase owns
 * the animation**. A `LaunchedEffect` frame loop only copies the tapper's
 * latest [SpectrumTapper.Snapshot] into state — no per-frame recomposition,
 * because the state is read inside the Canvas lambda, so only the draw phase
 * re-runs at 60 fps. The smoothing ([SpectrumAnalyzer.smoothAll]) is applied
 * in the draw pass too: fast attack up, slow decay down, which is what makes
 * raw FFT data read as "dancing" instead of flickering.
 *
 * Lifecycle: the tap is enabled only while this composable is on screen
 * ([DisposableEffect]), so playback costs nothing extra the moment the player
 * is collapsed. Paused playback zeroes the targets so the bars sink to the
 * floor rather than freezing mid-air. Reduced motion draws a static
 * mid-height pattern and never starts the frame loop.
 */
// SpectrumTapper is annotated @UnstableApi because it is built on media3's
// BaseAudioProcessor (see that class), so consuming it opts in here.
@OptIn(UnstableApi::class)
@Composable
fun LiveSpectrumBars(
    tapper: SpectrumTapper,
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    barCount: Int = SpectrumAnalyzer.BAR_COUNT
) {
    // The latest snapshot, copied off the atomic once per frame. Read inside
    // the draw lambda, so updating it re-draws without recomposing anything.
    val latest = remember { mutableStateOf(tapper.snapshot.get()) }
    // Smoothed bar heights — mutated in the draw phase, never read by compose.
    val smoothed = remember { FloatArray(barCount) }
    val ranges = remember(barCount) {
        SpectrumAnalyzer.barBinRanges(barCount, SpectrumTapper.BANDS)
    }

    DisposableEffect(tapper) {
        tapper.active.set(true)
        onDispose { tapper.active.set(false) }
    }

    LaunchedEffect(isPlaying, reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            latest.value = tapper.snapshot.get()
        }
    }

    Canvas(modifier = modifier) {
        val snapshot = latest.value
        val targets = if (reducedMotion) {
            // Static, calm pattern: a gentle arch. No loop runs in this mode,
            // so these values are recomputed only when the canvas redraws for
            // other reasons.
            FloatArray(barCount) { i ->
                val centre = (barCount - 1) / 2f
                0.35f + 0.2f * (1f - Math.abs(i - centre) / centre.coerceAtLeast(1f))
            }
        } else {
            SpectrumAnalyzer.magnitudesToBars(
                if (isPlaying) snapshot.bands else FloatArray(snapshot.bands.size),
                ranges,
                barCount
            )
        }
        SpectrumAnalyzer.smoothAll(smoothed, targets)

        val slot = size.width / barCount
        val barWidth = slot * 0.62f
        val corner = CornerRadius(barWidth / 2f)
        for (i in 0 until barCount) {
            // A small floor keeps every bar visible — a flat zero row reads as
            // "broken", a stub row reads as "quiet".
            val height = smoothed[i].coerceIn(0f, 1f) * size.height * 0.92f +
                size.height * 0.06f
            drawRoundRect(
                color = accentColor.copy(alpha = 0.5f + 0.5f * smoothed[i]),
                topLeft = Offset(
                    x = i * slot + (slot - barWidth) / 2f,
                    y = size.height - height
                ),
                size = Size(width = barWidth, height = height),
                cornerRadius = corner
            )
        }
    }
}
