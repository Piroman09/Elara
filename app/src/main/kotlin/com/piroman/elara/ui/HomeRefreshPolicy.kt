package com.piroman.elara.ui

/**
 * When Home's rows should be re-fetched on returning to the app.
 *
 * Pulled out of [MainViewModel] as a pure function for one reason: this is the
 * rule that decides whether the app makes a network call every time the user
 * switches back to it, and getting it wrong is invisible in a running app —
 * either Home quietly goes stale for hours, or every resume silently re-fetches
 * eight rows. Neither shows up as an error, so the rule is tested directly.
 *
 * The inputs are the four things that actually matter:
 *
 *  - [loading] — a batch is already in flight. A resume that follows a cold start
 *    arrives while the first fetch is still running, and fetching again there
 *    would double every request. This is checked first, so it always wins.
 *  - [requestedAtMs] — when the last batch was requested. `0` means no batch has
 *    started yet, which is not the same as "infinitely old": it means the caller
 *    has nothing to compare against and should stay out of the way.
 *  - [cameBackEmpty] — the last batch finished but left nothing to show, which is
 *    the signature of a *failed* fetch (opened offline, say). Without this, a
 *    failure would be indistinguishable from a success and Home would stay empty
 *    for the whole [maxAgeMs] even after connectivity returned.
 *  - [maxAgeMs] — the freshness window.
 */
internal object HomeRefreshPolicy {

    /**
     * @param requestedAtMs when Home's rows were last requested, or `0` if never.
     * @param nowMs current wall-clock time.
     * @param loading whether a batch is in flight right now.
     * @param cameBackEmpty whether the last completed batch left Home with
     *   nothing to show.
     * @param maxAgeMs how long rows stay fresh.
     */
    fun shouldRefresh(
        requestedAtMs: Long,
        nowMs: Long,
        loading: Boolean,
        cameBackEmpty: Boolean,
        maxAgeMs: Long
    ): Boolean {
        // An in-flight batch is authoritative — never stack a second one on it.
        if (loading) return false
        // Nothing has been fetched yet, so there is no age to judge. The initial
        // load is the ViewModel's job, not this one's.
        if (requestedAtMs <= 0L) return false
        // A failed fetch is retried immediately, whatever its age: an empty Home
        // is the one case where waiting is strictly worse than asking again.
        if (cameBackEmpty) return true
        return nowMs - requestedAtMs >= maxAgeMs
    }
}
