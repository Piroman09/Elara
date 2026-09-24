package com.piroman.elara.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piroman.elara.ui.theme.AuraViolet
import android.provider.Settings

/**
 * The title above a row of content — "Recently played", "Trending Songs",
 * "Playlists". One composable for the whole app.
 *
 * ## Why this exists
 *
 * It used to be a private function inside `HomeScreen`, and `LibraryScreen`
 * carried its own hand-written copy (`Color.White`, 18 sp, bold). Two copies of
 * the same visual role is exactly how the two screens drift apart, and the
 * requirement here is that they do **not** — so the style lives in one place and
 * every screen renders through it. Home's four call sites and Library's one are
 * now the same object at different text.
 *
 * ## The colour
 *
 * [AuraViolet], not [com.piroman.elara.ui.theme.AuraPurple]. This heading is the
 * one place a section is announced, so it wants the eye-catching end of the
 * palette — the same job the reference design gives its own accent. The two
 * candidates were measured against the near-black background rather than
 * guessed:
 *
 * - [AuraViolet] `#A78BFA` — 7.4:1 against [com.piroman.elara.ui.theme.AuraBg],
 *   and 6.9:1 against the lighter `AuraHomeGlow` at the top of Home.
 * - [AuraPurple] `#8B5CF6` — 4.75:1, dropping to 4.46:1 over the Home glow,
 *   which is under the 4.5:1 floor for text this size.
 *
 * [AuraViolet] is documented as the "active state" accent (shuffle on, repeat
 * on). Reusing it here does not collide with that: a heading is not a control,
 * so there is no state for a reader to misread. Being the *lighter* of the two
 * purples is also what makes the heading read as a heading against the cards.
 *
 * ## The equalizer
 *
 * When [isPlaying] is true, three 4 dp bars draw to the left of the text and
 * dance at staggered phases — the same visual language every streaming app
 * uses, and the same one `PixelSquigglySlider` already speaks on the full-screen
 * player. It answers "is something on right now?" from across the room, where a
 * playing card's tiny 20 dp badge does not read. Music apps also can't lean on
 * "Now Playing" section titles: the section names here are content names
 * ("Trending Songs"), so the indicator has to live *in* the chrome.
 *
 * The bars animate in the draw phase only — the phase `State` is read inside the
 * Canvas lambda, never in composition, so a playing heading costs zero
 * recompositions and shares the cost profile of the squiggly slider. Bars are
 * [AuraViolet] like the text they sit beside; violet-tinting them differently
 * would break the one-accent-per-heading rule. Hidden when idle — an idle
 * equalizer contradicts the state it exists to show.
 *
 * @param bottomPadding the gap to the content below. Defaulted rather than
 *   baked in, because the one caller that puts the heading inside a `Row`
 *   alongside buttons (`LibraryScreen`) already gets its spacing from the
 *   list's own `spacedBy` and would otherwise double up.
 */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 14.dp,
    isPlaying: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // The badge has to exist before the text moves over, or the swap-in
        // reads as a jump rather than as something appearing. `Visible` (or
        // `AnimatedVisibility` with fade+expand on both ends) keeps width from
        // popping in a single frame.
        modifier = modifier.padding(bottom = bottomPadding)
    ) {
        AnimatedVisibility(
            visible = isPlaying,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            EqualizerBars(
                barColor = AuraViolet,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Text(
            text = text,
            color = AuraViolet,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Three bars that dance while something plays.
 *
 * Standalone (public) because a card can want the same language without a
 * heading around it — see `HomeTrackCard`. Same draw-phase trick as
 * `PixelSquigglySlider`: one infinite transition, phases read only inside the
 * Canvas lambda, zero recompositions per frame.
 *
 * The stagger comes from sampling one shared phase at three fixed offsets — one
 * animation drives all three bars, so they stay coherent forever instead of
 * drifting the way three independent transitions would.
 *
 * @param playing false freezes the bars at their rest heights rather than
 *   hiding the composable — the idle shape is a real state, not an absence.
 * @param barColor bars draw in the accent of whatever surface carries them.
 * @param barWidth each bar's width; 4 dp matches the squiggly slider's stroke.
 * @param maxBarHeight the tallest bar; 18 dp fits a heading line, cards pass
 *   their own.
 */
@Composable
fun EqualizerBars(
    playing: Boolean = true,
    barColor: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 4.dp,
    maxBarHeight: Dp = 18.dp
) {
    // Reduced motion: when the user has switched system animations off
    // (animator duration scale 0), the bars hold their rest heights. An
    // ambient 60 fps loop is exactly what that setting exists to stop.
    val context = LocalContext.current
    val animationsDisabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
    val effectivePlaying = playing && !animationsDisabled

    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val phaseState: State<Float> = if (effectivePlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "equalizerPhase"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Canvas(modifier = modifier.width(barWidth * 3 + barWidth * 2)) {
        val phase = phaseState.value
        val barW = barWidth.toPx()
        val gap = barW
        val maxH = maxBarHeight.toPx()
        val midX = 0f
        repeat(3) { bar ->
            // Sample the shared phase at thirds of the cycle. `abs(sin)` keeps
            // every value positive and gives each bar two beats per cycle —
            // the standard equalizer look. Phase offsets in radians, not
            // fractions, so the rhythm does not depend on the period length.
            val local = phase + bar * (2.0f * Math.PI.toFloat() / 3f)
            val bounce = kotlin.math.abs(kotlin.math.sin(local))
            // Rest at a visible stub, peak at full height: the idle state is
            // still an equalizer, just a quiet one.
            val h = maxH * (0.25f + 0.75f * bounce)
            drawLine(
                color = barColor,
                start = Offset(midX + bar * (barW + gap) + barW / 2f, size.height),
                end = Offset(midX + bar * (barW + gap) + barW / 2f, size.height - h),
                strokeWidth = barW,
                cap = StrokeCap.Round
            )
        }
    }
}
