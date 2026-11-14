package app.captunes.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import app.captunes.data.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Pulls a header/background tint from album art with the Palette library. */
object ArtColors {
    private val cache = ConcurrentHashMap<String, Color>()

    fun fallback(seed: Int): Color = darken(paletteFor(seed).first())

    suspend fun extract(context: Context, coverArt: String?, seed: Int): Color {
        val key = coverArt ?: return fallback(seed)
        cache[key]?.let { return it }
        val url = Graph.source?.coverUri(key, 128) ?: return fallback(seed)
        val request = ImageRequest.Builder(context).data(url).size(128).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request)
        val color = if (result is SuccessResult) {
            withContext(Dispatchers.Default) {
                val palette = Palette.from(result.image.toBitmap()).generate()
                val swatch = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
                swatch?.let { darken(Color(it.rgb)) }
            } ?: fallback(seed)
        } else {
            fallback(seed)
        }
        cache[key] = color
        return color
    }

    private fun darken(c: Color): Color = lerp(c, Color.Black, 0.35f)
}

/** Animated tint for a cover; starts from the generated palette, then the real art color. */
@Composable
fun rememberArtColor(coverArt: String?, seed: Int): State<Color> {
    val context = LocalContext.current
    val target = produceState(ArtColors.fallback(seed), coverArt, seed) {
        value = runCatching { ArtColors.extract(context, coverArt, seed) }.getOrDefault(value)
    }
    return animateColorAsState(target.value, tween(400), label = "artColor")
}
