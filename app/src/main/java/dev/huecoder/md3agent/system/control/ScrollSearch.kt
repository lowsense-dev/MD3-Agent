package dev.huecoder.md3agent.system.control

sealed interface SearchOutcome {
    data class Found(val swipes: Int) : SearchOutcome
    data class EndReached(val swipes: Int) : SearchOutcome
    data class LimitReached(val swipes: Int) : SearchOutcome
    data class GestureFailed(val swipes: Int) : SearchOutcome
}

class ScrollSearch(
    private val isVisible: suspend () -> Boolean,
    private val fingerprint: suspend () -> Long,
    private val scrollOnce: suspend (SwipeDirection) -> Boolean,
) {
    suspend fun run(directions: List<SwipeDirection>, maxSwipes: Int): SearchOutcome {
        if (isVisible()) return SearchOutcome.Found(0)
        var swipes = 0
        var limited = false
        val share = (maxSwipes / directions.size.coerceAtLeast(1)).coerceAtLeast(1)
        for ((index, direction) in directions.withIndex()) {
            val limit = if (index == directions.lastIndex) maxSwipes else minOf(maxSwipes, swipes + share)
            var before = fingerprint()
            var ended = false
            while (swipes < limit) {
                if (!scrollOnce(direction)) return SearchOutcome.GestureFailed(swipes)
                swipes++
                if (isVisible()) return SearchOutcome.Found(swipes)
                val after = fingerprint()
                if (after == before) {
                    ended = true
                    break
                }
                before = after
            }
            if (swipes >= maxSwipes) return SearchOutcome.LimitReached(swipes)
            if (!ended) limited = true
        }
        return if (limited) SearchOutcome.LimitReached(swipes) else SearchOutcome.EndReached(swipes)
    }
}
