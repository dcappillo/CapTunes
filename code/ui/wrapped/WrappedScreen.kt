package app.captunes.ui.wrapped

import app.captunes.util.guarded
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.captunes.data.Wrapped
import app.captunes.data.WrappedStats
import app.captunes.ui.MainViewModel
import app.captunes.ui.components.CenteredMessage
import app.captunes.ui.theme.Coral
import app.captunes.ui.theme.Night
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Your year in music as swipeable story cards, built from the on-device play log. */
@Composable
fun WrappedScreen(vm: MainViewModel, year: Int, onClose: () -> Unit) {
    val stats by produceState<WrappedStats?>(null, year) {
        value = withContext(Dispatchers.IO) {
            vm.repo?.let { guarded("WrappedScreen") { Wrapped.compute(it.playLog(), year) }.getOrNull() }
        }
    }
    Box(Modifier.fillMaxSize().background(Night)) {
        val s = stats
        when {
            s == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Coral)
            s.isEmpty -> Box(Modifier.align(Alignment.Center)) {
                CenteredMessage(
                    "No plays logged for $year yet",
                    "Wrapped counts what you play in Cap Tunes from now on. Check back after some listening.",
                ) { Button(onClick = onClose) { Text("Close") } }
            }
            else -> Stories(s, onClose)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Stories(s: WrappedStats, onClose: () -> Unit) {
    val pages = remember(s) { wrappedPages(s, onClose) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTapGestures { offset ->
                            val target = if (offset.x < size.width * 0.3f) page - 1 else page + 1
                            if (target in pages.indices) scope.launch { pager.animateScrollToPage(target) }
                        }
                    },
            ) { pages[page]() }
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.indices.forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= pager.currentPage) Color.White else Color.White.copy(alpha = 0.3f)),
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Wrapped", tint = Color.White)
            }
        }
    }
}
