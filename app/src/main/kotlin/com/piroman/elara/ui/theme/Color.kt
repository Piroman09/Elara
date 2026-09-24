package com.aesthetic.spotify.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * ELARA is **black and purple**: a near-black base carrying violet accents.
 *
 * The first pass at "make the ui purple" tinted every surface violet, which was
 * too far — the app read as purple-on-purple and lost the contrast that makes a
 * dark theme legible. The surfaces below are black again (with only a faint
 * violet cast so they sit in the same family), and the purple lives where it
 * earns attention: the primary accent, active states, the playlist gradients,
 * the home glow and the full-screen player's glow.
 *
 * Semantic colours are deliberately *not* violet: an error is red, a download is
 * emerald, a warning is amber, and Spotify's import affordance is Spotify green.
 * Re-tinting those would cost the ability to read state at a glance.
 */

/** Near-black app background, with the faintest violet cast. */
val AuraBg = Color(0xFF08070C)

/** Signature violet. The app's primary accent. */
val AuraPurple = Color(0xFF8B5CF6)

/**
 * Light violet. Carries the "active / selected" role — shuffle on, repeat on,
 * the current lyric line, an enabled toggle.
 *
 * This replaced a cyan (`#06B6D4`) that was doing that job. Cyan sat outside the
 * app's hue entirely, so every active control read as a foreign accent;
 * [AuraViolet] is unmistakably part of the theme while staying lighter and
 * brighter than [AuraPurple], which is what lets an active state still pop.
 */
val AuraViolet = Color(0xFFA78BFA)

/** Warm end of the signature gradient — a soft lavender. */
val AuraLavender = Color(0xFFC4B5FD)

/**
 * Elevated surfaces for dialogs and panels. Kept black rather than violet so the
 * accents above have something to sit against; separated from [AuraBg] by a
 * hairline outline rather than a visible card colour.
 */
val AuraSurface = Color(0xFF100E15)
val AuraContainer = Color(0xFF16141C)
val AuraOutline = Color(0xFF2A2733)
val AuraSuccess = Color(0xFF22C55E)

/**
 * Semantic text colours. Before these existed the same three roles were spelled
 * `Color.Gray`, `Color(0xFF8A8A96)` and `Color(0xFF9CA3AF)` in different files,
 * so secondary text drifted between three different greys.
 */
val AuraTextPrimary = Color(0xFFFFFFFF)
val AuraTextSecondary = Color(0xFF9A9AA8)
val AuraTextTertiary = Color(0xFF6E6E7A)

/** Destructive / failure accent (delete buttons, playback errors). */
val AuraError = Color(0xFFF87171)

/**
 * The emerald used for the downloads affordance — the tick on a saved song and
 * the "Downloads" card gradient. Distinct from [AuraSuccess], which is the
 * Spotify-import green.
 */
val AuraDownload = Color(0xFF34D399)

/** Attention / caution accent (diagnostics affordances). */
val AuraWarning = Color(0xFFFBBF24)

/**
 * The standard glass card fill and hairline border. Neutral white at low alpha,
 * so a card reads as black glass — violet-tinting these was what made the
 * surfaces look purple-on-purple. Alpha is baked in so these are compile-time
 * constants and identical everywhere.
 */
val AuraCard = Color(0x0FFFFFFF)
val AuraCardStrong = Color(0x14FFFFFF)
val AuraCardBorder = Color(0x14FFFFFF)

/** Barely-there fill for empty-state rows that shouldn't read as cards. */
val AuraCardSubtle = Color(0x08FFFFFF)

/** Text-field fill (unfocused). The focused state uses [AuraCardStrong]. */
val AuraField = Color(0x0DFFFFFF)

/**
 * Floating surface behind dropdown menus and alert dialogs. Every `DropdownMenu`
 * and `AlertDialog` in the app was carrying its own `Color(0xFF1A1A1C)` literal.
 */
val AuraMenu = Color(0xFF18161E)

/**
 * Spotify's brand green. Declared twice before this — once privately in
 * `ImportSpotifyDialog` and again in `LibraryScreen` — for the same accent.
 * Kept green on purpose: it marks a Spotify-branded action, so it should not
 * follow the app's own hue.
 */
val SpotifyBrand = Color(0xFF1DB954)

/**
 * The surfaces that used to be spelled out as file-local literals in three
 * different components — `#15151B` in `FloatingPlayer`, `#16161D` in
 * `QueueDrawer`, `#0B0B10` in `MainScaffold` — none of which matched
 * [AuraContainer]. They are the same material and now say so.
 */
val AuraPlayerSurface = AuraContainer
val AuraScaffoldSurface = Color(0xFF0C0B10)

/**
 * The glow behind the Home header and the top of the full-screen player. This is
 * one of the few places the purple is allowed to fill a large area, because it
 * is a soft gradient into black rather than a flat purple surface.
 */
val AuraHomeGlow = Color(0xFF140F20)
