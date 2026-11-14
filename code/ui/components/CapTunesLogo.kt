package app.captunes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.captunes.R
import app.captunes.ui.theme.Coral

val BoltYellow = Color(0xFFFFD23F)
private val Cream = Color(0xFFF4F1EA)
private val Ink = Color(0xFF121212)

/** Bungee, bundled in res/font (SIL Open Font License). */
val Bungee = FontFamily(Font(R.font.bungee))

/**
 * The Cap Tunes "Sound Bolt" mark: a coral disc with sound bars and a
 * yellow lightning bolt. Matches the launcher icon. Drawn on a 100-unit grid.
 */
@Composable
fun CapTunesLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 76f
        // The disc spans 12..88 on the 100 grid; shift so it fills the canvas.
        fun p(x: Float, y: Float) = Offset((x - 12f) * u, (y - 12f) * u)
        drawCircle(Coral, radius = 38f * u, center = p(50f, 50f))
        bar(u, ::p, 24f, 40f, 20f)
        bar(u, ::p, 34f, 33f, 34f)
        bar(u, ::p, 63f, 33f, 34f)
        bar(u, ::p, 73f, 41f, 18f)
        val bolt = Path().apply {
            val pts = listOf(55f to 20f, 42f to 52f, 51f to 52f, 46f to 80f, 62f to 44f, 54f to 44f, 60f to 20f)
            pts.forEachIndexed { i, (x, y) ->
                val o = p(x, y)
                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
            close()
        }
        drawPath(bolt, BoltYellow)
        drawPath(bolt, Ink, style = Stroke(width = 1.5f * u, join = StrokeJoin.Round))
    }
}

private fun DrawScope.bar(u: Float, p: (Float, Float) -> Offset, x: Float, y: Float, h: Float) {
    drawRoundRect(
        color = Cream,
        topLeft = p(x, y),
        size = Size(7f * u, h * u),
        cornerRadius = CornerRadius(3.5f * u, 3.5f * u),
    )
}

/** A plain yellow lightning bolt, used in the wordmark. */
@Composable
fun Bolt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width / 24f
        val h = size.height / 34f
        val path = Path().apply {
            moveTo(15f * w, 0f)
            lineTo(2f * w, 19f * h)
            lineTo(11f * w, 19f * h)
            lineTo(8f * w, 34f * h)
            lineTo(22f * w, 12f * h)
            lineTo(13f * w, 12f * h)
            close()
        }
        drawPath(path, BoltYellow)
    }
}

/** The CAP TUNES wordmark: the Sound Bolt app icon, CAP in cream, TUNES in coral, set in Bungee. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, size: TextUnit = 26.sp, boltHeight: Dp = 34.dp, showMark: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showMark) {
            CapTunesLogo(Modifier.size(boltHeight))
            Spacer(Modifier.width(10.dp))
        }
        Text("CAP", fontFamily = Bungee, fontSize = size, color = Cream)
        Spacer(Modifier.width(8.dp))
        Text("TUNES", fontFamily = Bungee, fontSize = size, color = Coral)
    }
}
