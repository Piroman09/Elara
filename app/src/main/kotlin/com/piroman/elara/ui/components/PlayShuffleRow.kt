package com.piroman.elara.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The Play / Shuffle pair that heads every track collection, with an optional
 * trailing action.
 *
 * This existed as **three** separate copies — the playlist header, the browse
 * screen and the Downloads screen — which is exactly the drift the shared-
 * component rule exists to prevent. They had already diverged: two of the three
 * omitted `maxLines = 1` on their labels, so only the playlist header was
 * protected against a squeezed label.
 *
 * **`FlowRow`, not `Row`, unconditionally.** A `Row` squeezes children that do
 * not fit rather than letting them keep their measured width, and each
 * icon+label button carries roughly 78 dp of non-text chrome — a 24 dp icon, a
 * 6 dp gap and Material's 24 dp content padding on each side. With a trailing
 * "Download all" that is ~370 dp against the ~328 dp a 360 dp phone leaves after
 * a screen's 16 dp padding, which broke the label mid-word into
 * "Dow / nloa / d all". `FlowRow` keeps each button at its natural width and
 * moves the one that does not fit onto the next line, so two buttons stay on one
 * line at any width and three wrap only when they must. Using it for the
 * two-button callers too means the latent version of that bug cannot come back
 * if a third button is ever added.
 *
 * @param onPlay invoked by the Play button.
 * @param onShuffle invoked by the Shuffle button. The caller picks the track —
 *   this row deliberately does not, so it stays free of playback policy.
 * @param enabled false dims both buttons. Pass the collection's non-emptiness:
 *   playing "the first track" of nothing is meaningless.
 * @param trailing optional extra button, placed after Shuffle and wrapping with
 *   it. The playlist header passes its "Download all"; the other callers pass
 *   nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayShuffleRow(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onPlay,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.3f)
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Play", fontWeight = FontWeight.Bold, maxLines = 1)
        }

        OutlinedButton(
            onClick = onShuffle,
            enabled = enabled,
            // contentColor rather than a hardcoded `tint`/`color` on the icon and
            // label: those ignore `enabled`, so a disabled Shuffle used to keep
            // a fully white label and look tappable while doing nothing.
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.38f)
            )
        ) {
            Icon(Icons.Default.Shuffle, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Shuffle", fontWeight = FontWeight.Bold, maxLines = 1)
        }

        trailing?.invoke()
    }
}
