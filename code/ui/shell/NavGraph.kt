package app.captunes.ui.shell

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.captunes.data.Card
import app.captunes.data.Kind
import app.captunes.ui.MainViewModel
import app.captunes.ui.collection.ArtistScreen
import app.captunes.ui.collection.CollectionScreen
import app.captunes.ui.home.HomeScreen
import app.captunes.ui.library.LibraryScreen
import app.captunes.ui.search.SearchScreen
import app.captunes.ui.wrapped.WrappedScreen
import app.captunes.ui.theme.Night
import app.captunes.ui.theme.TextDim

enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Search("search", "Search", Icons.Outlined.Search, Icons.Filled.Search),
    Library("library", "Your Library", Icons.AutoMirrored.Filled.LibraryBooks, Icons.AutoMirrored.Filled.LibraryBooks),
}

fun NavController.openCard(card: Card) {
    if (card.kind == Kind.Artist) openArtist(card.id)
    else navigate("collection/${card.kind.name}/${Uri.encode(card.id.ifEmpty { "_" })}")
}

fun NavController.openAlbum(id: String) = navigate("collection/${Kind.Album.name}/${Uri.encode(id)}")

fun NavController.openArtist(id: String) = navigate("artist/${Uri.encode(id)}")

@Composable
fun CapTunesNavHost(nav: NavHostController, vm: MainViewModel, onAccount: () -> Unit, modifier: Modifier) {
    NavHost(
        navController = nav,
        startDestination = Tab.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        composable(Tab.Home.route) {
            HomeScreen(vm, onOpen = nav::openCard, onAccount = onAccount, onWrapped = { nav.navigate("wrapped/$it") })
        }
        composable("wrapped/{year}") { entry ->
            val year = entry.arguments?.getString("year")?.toIntOrNull() ?: java.time.Year.now().value
            WrappedScreen(vm, year, onClose = { nav.popBackStack() })
        }
        composable(Tab.Search.route) { SearchScreen(vm, onOpen = nav::openCard) }
        composable(Tab.Library.route) { LibraryScreen(vm, onOpen = nav::openCard, onAccount = onAccount) }
        composable("collection/{kind}/{id}") { entry ->
            val kind = entry.arguments?.getString("kind")?.let { k -> Kind.entries.firstOrNull { it.name == k } }
                ?: Kind.Album
            val id = entry.arguments?.getString("id").orEmpty().let { if (it == "_") "" else it }
            CollectionScreen(vm, kind, id, onBack = { nav.popBackStack() }, onArtist = nav::openArtist)
        }
        composable("artist/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ArtistScreen(vm, id, onBack = { nav.popBackStack() }, onOpen = nav::openCard)
        }
    }
}

@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val haptics = LocalHapticFeedback.current
    NavigationBar(containerColor = Night.copy(alpha = 0.97f)) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(tab)
                },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    unselectedIconColor = TextDim,
                    unselectedTextColor = TextDim,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

fun NavHostController.selectTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
