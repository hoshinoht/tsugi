package dev.cantabile.tsugi.data

/**
 * LTA abbreviates stop names ("Opp Bishan Stn", "Bef Admiralty Pr Sch"); spelled-out words in a
 * query are folded to the same short forms so either matches.
 */
private val ABBREVIATIONS = mapOf(
    "station" to "stn", "mrt" to "stn", "lrt" to "stn",
    "interchange" to "int", "terminal" to "ter", "terminus" to "ter",
    "block" to "blk", "opposite" to "opp", "before" to "bef", "after" to "aft",
    "avenue" to "ave", "street" to "st", "road" to "rd", "drive" to "dr", "crescent" to "cres",
    "centre" to "ctr", "center" to "ctr", "school" to "sch", "primary" to "pr", "secondary" to "sec",
    "industrial" to "ind", "hospital" to "hosp", "junction" to "jct", "building" to "bldg",
    "condominium" to "condo", "church" to "ch", "point" to "pt", "mount" to "mt",
)

/** Lower-case words with punctuation dropped and spelled-out words abbreviated as LTA does. */
fun searchTokens(text: String): List<String> =
    text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.map { ABBREVIATIONS[it] ?: it }

/** True if [a] and [b] differ by at most one insertion, deletion, substitution or swap. */
fun withinOneEdit(a: String, b: String): Boolean {
    if (a == b) return true
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    if (a.length == b.length) {
        // Substitution, or two adjacent letters swapped.
        if (a.substring(i + 1) == b.substring(i + 1)) return true
        return i + 1 < a.length && a[i] == b[i + 1] && a[i + 1] == b[i] && a.substring(i + 2) == b.substring(i + 2)
    }
    val (long, short) = if (a.length > b.length) a to b else b to a
    return long.substring(i + 1) == short.substring(i)
}

/** Typos are only forgiven in words this long; shorter ones must match as typed. */
private const val TYPO_MIN_LENGTH = 4

/**
 * Ranks [stops] for [query]: exact and prefix stop codes, then names starting with or containing
 * the query, then roads, then every word of the query found in any order (as a word prefix), and
 * last the same allowing one typo per longer word. Ties sort by name.
 */
fun searchStops(stops: List<BusStop>, query: String, limit: Int = 50): List<BusStop> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val words = searchTokens(q)
    return stops.asSequence()
        .mapNotNull { stop ->
            val name = stop.description.lowercase()
            val rank = when {
                stop.code == q -> 0
                stop.code.startsWith(q) -> 1
                name.startsWith(q) -> 2
                name.contains(q) -> 3
                stop.road.lowercase().contains(q) -> 4
                words.isEmpty() -> null
                else -> {
                    val tokens = searchTokens("${stop.description} ${stop.road}")
                    when {
                        words.all { w -> tokens.any { it.startsWith(w) } } -> 5
                        words.all { w -> tokens.any { t -> t.startsWith(w) || (w.length >= TYPO_MIN_LENGTH && (withinOneEdit(w, t) || withinOneEdit(w, t.take(w.length)))) } } -> 6
                        else -> null
                    }
                }
            }
            rank?.let { it to stop }
        }
        .sortedWith(compareBy({ it.first }, { it.second.description }))
        .take(limit)
        .map { it.second }
        .toList()
}
