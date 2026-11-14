package app.captunes.data

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class ArtistStat(val name: String, val minutes: Long, val coverArt: String?, val plays: Int)

@Immutable
data class WrappedStats(
    val year: Int,
    val minutes: Long,
    val plays: Int,
    val songs: Int,
    val artists: Int,
    val skips: Int,
    val topSongs: List<Pair<Track, Int>>,
    val topArtists: List<ArtistStat>,
    val topGenres: List<Pair<String, Int>>,
    val biggestDay: String?,
    val biggestDayMinutes: Long,
    val peakHour: Int?,
    val firstSong: Track?,
) {
    val isEmpty get() = plays == 0
}

/** Your year in music, computed from the on-device play log. */
object Wrapped {
    fun compute(log: PlayLog, year: Int, zone: ZoneId = ZoneId.systemDefault()): WrappedStats {
        val from = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = log.rows(from, to).filter { it.outcome != Outcome.Dislike }
        val counted = rows.filter { it.outcome != Outcome.EarlySkip }

        val bySong = counted.groupBy { it.track.id }
        val topSongs = bySong.values.sortedByDescending { it.size }.take(5).map { it.last().track to it.size }

        val topArtists = rows.groupBy { it.track.artist }
            .map { (name, plays) ->
                ArtistStat(
                    name = name,
                    minutes = plays.sumOf { it.listenedMs } / 60_000,
                    coverArt = plays.firstNotNullOfOrNull { it.track.coverArt },
                    plays = plays.count { it.outcome != Outcome.EarlySkip },
                )
            }
            .sortedByDescending { it.minutes }.take(5)

        val topGenres = counted.mapNotNull { it.track.genre?.takeIf { g -> g.isNotBlank() } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
            .map { it.key to it.value }

        fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)
        val days = rows.groupBy { date(it.startedAt).toLocalDate() }
            .mapValues { (_, r) -> r.sumOf { it.listenedMs } / 60_000 }
        val bestDay = days.maxByOrNull { it.value }
        val peakHour = counted.groupingBy { date(it.startedAt).hour }.eachCount().maxByOrNull { it.value }?.key

        return WrappedStats(
            year = year,
            minutes = rows.sumOf { it.listenedMs } / 60_000,
            plays = counted.size,
            songs = bySong.size,
            artists = counted.map { it.track.artist }.distinct().size,
            skips = rows.count { it.outcome == Outcome.EarlySkip || it.outcome == Outcome.LateSkip },
            topSongs = topSongs,
            topArtists = topArtists,
            topGenres = topGenres,
            biggestDay = bestDay?.key?.format(DateTimeFormatter.ofPattern("MMMM d")),
            biggestDayMinutes = bestDay?.value ?: 0,
            peakHour = peakHour,
            firstSong = counted.firstOrNull()?.track,
        )
    }
}
