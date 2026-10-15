package com.piroman.elara.playback

/**
 * When to give up skipping tracks that will not play.
 *
 * Split out of [PlaybackManager.skipFailedTrack] for the same reason
 * [QueueOrder] is separate from the queue machinery: the rule is invisible in
 * the code that consumes it, and both of its failure modes are silent.
 *
 * Too eager — the budget resets on every track transition, so against a queue
 * that is uniformly unplayable (offline with a streamed album) the app walks the
 * whole thing at 600 ms a song: a burst of network requests, a drained battery,
 * and a queue the user never chose to skip through. Too shy — the first bad
 * track ends the session, which is the bug this exists to fix: one unresolvable
 * song used to stop an album dead.
 *
 * Holds no Android or ExoPlayer types, so it is exercised directly by
 * `FailedTrackPolicyTest`.
 */
internal object FailedTrackPolicy {

    /**
     * Consecutive failures tolerated before ELARA stops skipping and leaves the
     * error on screen. Three: enough to walk past a run of dead tracks, few
     * enough that being offline does not turn into a search-party through the
     * user's data.
     */
    const val MAX_CONSECUTIVE_FAILURES = 3

    /**
     * Whether to move past a track that just failed.
     *
     * @param failures consecutive failures *including* the one that just
     *   happened — the caller has already counted it.
     * @param queueSize entries in the queue. A single-entry queue has nowhere to
     *   move on to, and reporting the error there is the honest answer rather
     *   than a skip that silently does nothing.
     *
     * A pause is not consulted: this only answers whether another track could be
     * tried. The caller decides whether the user still wants sound.
     */
    fun shouldSkip(failures: Int, queueSize: Int): Boolean =
        queueSize > 1 && failures in 1..MAX_CONSECUTIVE_FAILURES
}
