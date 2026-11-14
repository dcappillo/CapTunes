package app.captunes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import app.captunes.data.Graph
import app.captunes.ui.theme.Surface2
import kotlin.math.abs

/** Gradient pairs for generated covers (mixes, genres, songs with no art). */
val ArtPalettes = listOf(
    listOf(Color(0xFFFF5A36), Color(0xFF7C2BFF)),
    listOf(Color(0xFF00C2A8), Color(0xFF0B3D91)),
    listOf(Color(0xFFFFC23D), Color(0xFFE5376B)),
    listOf(Color(0xFF7C5CFF), Color(0xFF1FB6FF)),
    listOf(Color(0xFF3DDC84), Color(0xFF155E63)),
    listOf(Color(0xFFFF7EB3), Color(0xFF5A2A83)),
    listOf(Color(0xFFFF8A3D), Color(0xFF8A1C1C)),
    listOf(Color(0xFF4DD0E1), Color(0xFF6A1B9A)),
)

fun paletteFor(seed: Int): List<Color> = ArtPalettes[abs(seed) % ArtPalettes.size]

/** Album art from Navidrome, or an original generated cover when there is none. */
@Composable
fun Cover(
    coverArt: String?,
    seed: Int,
    label: String,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    sizePx: Int = 500,
    showLabel: Boolean = false,
) {
    val context = LocalContext.current
    val request = remember(coverArt, sizePx) {
        Graph.source?.coverUri(coverArt, sizePx)?.let {
            ImageRequest.Builder(context).data(it).crossfade(true).build()
        }
    }
    Box(modifier.clip(shape)) {
        if (request == null) {
            GeneratedArt(seed, label, showLabel, Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = request,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize().background(Surface2)) },
                error = { GeneratedArt(seed, label, showLabel, Modifier.fillMaxSize()) },
            )
        }
    }
}

/** Bold abstract cover: gradient, circles and a tilted band, optional title. */
@Composable
fun GeneratedArt(seed: Int, label: String, showLabel: Boolean, modifier: Modifier = Modifier) {
    val colors = remember(seed) { paletteFor(seed) }
    Box(modifier.background(Brush.linearGradient(colors))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val variant = abs(seed) % 3
            drawCircle(Color.White.copy(alpha = 0.16f), radius = w * 0.42f, center = Offset(w * 0.9f, h * 0.12f))
            drawCircle(Color.Black.copy(alpha = 0.18f), radius = w * 0.3f, center = Offset(w * 0.1f, h * 0.95f))
            rotate(degrees = -20f + variant * 15f) {
                drawRect(
                    Color.White.copy(alpha = 0.10f),
                    topLeft = Offset(-w * 0.2f, h * (0.35f + variant * 0.08f)),
                    size = Size(w * 1.4f, h * 0.14f),
                )
            }
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
    }
}
