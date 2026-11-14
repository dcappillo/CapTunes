package app.captunes.ui.wrapped

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.captunes.data.WrappedStats
import app.captunes.ui.components.ArtPalettes
import app.captunes.ui.components.Cover
import app.captunes.ui.components.CapTunesLogo

/** The story cards, in order. Cards with nothing to show are skipped. */
fun wrappedPages(s: WrappedStats, onClose: () -> Unit): List<@Composable () -> Unit> = buildList<@Composable () -> Unit> {
    add { Intro(s) }
    add { Minutes(s) }
    s.topSongs.firstOrNull()?.let { (track, plays) ->
        add {
            Story(1) {
                Label("Your song of the year")
                Cover(track.coverArt, track.albumId.hashCode(), track.album, Modifier.size(240.dp), RoundedCornerShape(8.dp), 700)
                Big(track.title, 34.sp)
                Text(track.artist, color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp)
                Text("You played it $plays times.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (s.topArtists.isNotEmpty()) add {
        Story(2) {
            Label("Your top artists")
            s.topArtists.forEachIndexed { i, a ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp))
                    Cover(a.coverArt, a.name.hashCode(), a.name, Modifier.size(56.dp), CircleShape, 200)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("%,d minutes".format(a.minutes), color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
    if (s.topSongs.size > 1) add {
        Story(3) {
            Label("Your top songs")
            s.topSongs.forEachIndexed { i, (t, plays) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp))
                    Cover(t.coverArt, t.albumId.hashCode(), t.album, Modifier.size(56.dp), RoundedCornerShape(4.dp), 200)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${t.artist} · $plays plays", color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                    }
                }
            }
        }
    }
    if (s.topGenres.isNotEmpty()) add {
        Story(4) {
            Label("Your top genres")
            s.topGenres.forEachIndexed { i, (g, _) ->
                Text(g, fontSize = (40 - i * 5).sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 1f - i * 0.12f))
            }
        }
    }
    add { Habits(s) }
    add { Summary(s, onClose) }
}

@Composable
private fun Intro(s: WrappedStats) = Story(0) {
    CapTunesLogo(Modifier.size(96.dp))
    Big("Your ${s.year}\nWrapped", 52.sp)
    Text("Tap the right side to keep going.", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
}

@Composable
private fun Minutes(s: WrappedStats) = Story(5) {
    Label("You listened for")
    Big("%,d".format(s.minutes), 72.sp)
    Text("minutes", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    val hours = s.minutes / 60.0
    Text(
        "That's %.0f hours across %,d plays, %,d songs and %,d artists.".format(hours, s.plays, s.songs, s.artists),
        color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp,
    )
}

@Composable
private fun Habits(s: WrappedStats) = Story(6) {
    Label("Your listening habits")
    s.biggestDay?.let {
        Text("Biggest day", color = Color.White.copy(alpha = 0.8f))
        Big("$it · ${s.biggestDayMinutes} min", 30.sp)
    }
    s.peakHour?.let {
        Text("You press play most at", color = Color.White.copy(alpha = 0.8f))
        Big(hourLabel(it), 30.sp)
    }
    Text("Skips", color = Color.White.copy(alpha = 0.8f))
    Big("%,d".format(s.skips), 30.sp)
    Text("Every one taught your Daily Mixes something.", color = Color.White.copy(alpha = 0.85f))
}

@Composable
private fun Summary(s: WrappedStats, onClose: () -> Unit) = Story(7) {
    Label("${s.year} in a nutshell")
    Fact("Minutes listened", "%,d".format(s.minutes))
    s.topArtists.firstOrNull()?.let { Fact("Top artist", it.name) }
    s.topSongs.firstOrNull()?.let { Fact("Top song", it.first.title) }
    s.topGenres.firstOrNull()?.let { Fact("Top genre", it.first) }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onClose,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) { Text("Done", fontWeight = FontWeight.Bold) }
}

@Composable
private fun Story(palette: Int, content: @Composable ColumnScope.() -> Unit) {
    val colors = ArtPalettes[palette % ArtPalettes.size]
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors))) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 56.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            content = content,
        )
    }
}

@Composable
private fun Label(text: String) = Text(text.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

@Composable
private fun Big(text: String, size: TextUnit) =
    Text(text, fontSize = size, lineHeight = size * 1.05f, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)

@Composable
private fun Fact(label: String, value: String) = Column {
    Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
    Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun hourLabel(h: Int) = when {
    h == 0 -> "midnight"
    h < 12 -> "$h AM"
    h == 12 -> "noon"
    else -> "${h - 12} PM"
}
