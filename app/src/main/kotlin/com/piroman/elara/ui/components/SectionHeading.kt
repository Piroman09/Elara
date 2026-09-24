package com.piroman.elara.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piroman.elara.ui.theme.AuraViolet

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
 * @param bottomPadding the gap to the content below. Defaulted rather than
 *   baked in, because the one caller that puts the heading inside a `Row`
 *   alongside buttons (`LibraryScreen`) already gets its spacing from the
 *   list's own `spacedBy` and would otherwise double up.
 */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 14.dp
) {
    Text(
        text = text,
        color = AuraViolet,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(bottom = bottomPadding)
    )
}
