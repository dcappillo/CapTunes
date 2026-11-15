package app.captunes.data

/** Reads the .lrc synced-lyrics format. Plain Kotlin, so it can be unit-tested. */
object Lrc {
    private val stamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /**
     * Parses "[mm:ss.xx] text" lines into (start time in ms, text), earliest first.
     * A line may carry several timestamps; lines without one are skipped.
     */
    fun parse(lrc: String): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        for (raw in lrc.lines()) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = raw.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val (mm, ss, frac) = m.destructured
                val fracMs = when (frac.length) {
                    0 -> 0
                    1 -> frac.toInt() * 100
                    2 -> frac.toInt() * 10
                    else -> frac.take(3).toInt()
                }
                out += (mm.toLong() * 60_000 + ss.toLong() * 1000 + fracMs) to text
            }
        }
        return out.sortedBy { it.first }
    }
}
