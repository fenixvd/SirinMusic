package ru.rainedev.sirinmusic

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import ru.rainedev.sirinmusic.playback.PlayerUi
import ru.rainedev.sirinmusic.data.ConnectLink
import ru.rainedev.sirinmusic.ui.*
import ru.rainedev.sirinmusic.ui.theme.SirinMusicTheme

class MainActivity : ru.rainedev.sirinmusic.SirinActivity() {
    // Same instance as viewModel() in SirinContent (same owner and default key).
    private val vm: AppViewModel by viewModels { AppViewModel.Factory(application as SirinApp) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val app = application as SirinApp
        app.updates.checkOnLaunch()
        // musik://connect?url=…&token=… from the system camera. After recreation the Intent
        // is the same one: an unused link is still in the ViewModel (or, after process
        // death, restored from the Intent by its saved marker); a used one must not come back.
        if (savedInstanceState == null) linkFrom(intent)?.let(vm.pendingLink::offer)
        else vm.pendingLink.restore(linkFrom(intent), savedInstanceState.getString(KEY_PENDING_LINK))
        vm.pendingPlayerNavigation.restore(
            intent?.action == PendingPlayerNavigation.ACTION_OPEN_PLAYER,
            savedInstanceState?.getBoolean(KEY_PENDING_PLAYER),
        )
        setContent {
            val appearance by app.settings.appearance.collectAsStateWithLifecycle()
            SirinMusicTheme(appearance) { Surface(Modifier.fillMaxSize()) {
                SirinContent(app, onPlayerNavigationConsumed = {
                    if (intent?.action == PendingPlayerNavigation.ACTION_OPEN_PLAYER) {
                        setIntent(Intent(intent).setAction(null))
                    }
                })
            } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The accepted link becomes the Activity's Intent, so a recreated Activity
        // reads this link (and matches its saved marker), not the launch one.
        setIntent(intent)
        linkFrom(intent)?.let(vm.pendingLink::offer)
        if (intent.action == PendingPlayerNavigation.ACTION_OPEN_PLAYER) vm.pendingPlayerNavigation.offer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        vm.pendingLink.marker()?.let { outState.putString(KEY_PENDING_LINK, it) }
        outState.putBoolean(KEY_PENDING_PLAYER, vm.pendingPlayerNavigation.requested.value)
    }

    private fun linkFrom(intent: Intent?): ConnectLink? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString?.let(ConnectLink::parse)

    private companion object {
        const val KEY_PENDING_LINK = "pending_link_marker"
        const val KEY_PENDING_PLAYER = "pending_player_navigation"
    }
}

private data class Destination(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SirinContent(app: SirinApp, onPlayerNavigationConsumed: () -> Unit) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory(app))
    val state by vm.state.collectAsStateWithLifecycle()
    val pendingLink by vm.pendingLink.value.collectAsStateWithLifecycle()
    val openPlayer by vm.pendingPlayerNavigation.requested.collectAsStateWithLifecycle()
    val player by vm.playback.ui.collectAsStateWithLifecycle()
    FeedbackToast(state.message, player.error, vm::dismissMessage, vm.playback::dismissError)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, vm) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.onForeground()
            while (true) {
                delay(30_000)
                vm.checkLibraryChanges()
            }
        }
    }
    if (!state.loggedIn) {
        LoginScreen(vm.savedUrl, vm.savedToken, state.checking, state.loginError, vm::login,
            pendingLink, vm.pendingLink::consume,
            onPasswordSubmit = vm::loginPassword, profiles = state.loginProfiles,
            onChooseProfile = vm::chooseLoginProfile, onCancelProfiles = vm::cancelAccountLogin,
            onAnonymousSubmit = vm::loginWithoutToken)
        return
    }
    LaunchedEffect(pendingLink) {
        if (pendingLink != null && vm.pendingLink.consume() != null) {
            vm.showMessage(strings(R.string.main_activity_1))
        }
    }
    key(vm.savedUrl, vm.savedToken) {
        ConnectedContent(app, vm, state, player, openPlayer, onPlayerNavigationConsumed)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectedContent(app: SirinApp, vm: AppViewModel, state: AppState, player: PlayerUi,
    openPlayer: Boolean, onPlayerNavigationConsumed: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    var showPassport by remember(route, player.current?.key, vm.savedUrl, vm.savedToken) { mutableStateOf(false) }
    if (route == "now" && showPassport) player.current?.let { track ->
        TrackPassportPopup(track, app.api, vm.savedUrl + "|" + vm.savedToken) { showPassport = false }
    }
    LaunchedEffect(route) {
        if (route == "home" || route == "library" || route.startsWith("pick/")) vm.refreshContent(force = false)
    }
    val destinations = listOf(Destination("home", strings(R.string.main_activity_2), Icons.Rounded.Home),
        Destination("library", strings(R.string.main_activity_3), Icons.Rounded.LibraryMusic),
        Destination("playlists", strings(R.string.main_activity_4), Icons.AutoMirrored.Rounded.QueueMusic),
        Destination("profile", strings(R.string.main_activity_5), Icons.Rounded.Person))
    val isDetail = route == "now" || route == "lyrics" || route == "favorites" || route.startsWith("playlist/") || route.startsWith("pick/")
    var addTrackId by rememberSaveable { mutableStateOf<Long?>(null) }
    fun mainRoute(target: String) {
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true; restoreState = true
        }
    }
    fun openNow() { nav.navigate("now") { launchSingleTop = true } }
    LaunchedEffect(openPlayer, entry) {
        if (openPlayer && entry != null) {
            openNow()
            vm.pendingPlayerNavigation.consume()
            onPlayerNavigationConsumed()
        }
    }
    val title = when {
        route == "now" -> strings(R.string.main_activity_6)
        route == "lyrics" -> strings(R.string.main_activity_7)
        route == "favorites" -> strings(R.string.main_activity_8)
        route.startsWith("pick/") -> strings(R.string.main_activity_9)
        route.startsWith("playlist/") -> strings(R.string.main_activity_10)
        else -> destinations.firstOrNull { it.route == route }?.label ?: "Sirin Music"
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (wide && !isDetail) NavigationRail {
                destinations.forEach { destination ->
                    NavigationRailItem(selected = route == destination.route, onClick = { mainRoute(destination.route) },
                        icon = { Icon(destination.icon, null) }, label = { Text(destination.label) })
                }
            }
            Scaffold(modifier = Modifier.weight(1f),
                topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
                    if (isDetail) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, strings(R.string.about_activity_2)) }
                }, actions = {
                    if (route == "now") {
                        TrackShareButton(player.current, vm::artworkUrl)
                        IconButton(onClick = { showPassport = true }, enabled = player.current != null) {
                            Icon(Icons.Outlined.Info, strings(R.string.track_passport_title))
                        }
                    }
                    if (!isDetail) IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                        Icon(Icons.Rounded.Settings, strings(R.string.main_activity_11))
                    }
                }) },
                bottomBar = {
                    if (!isDetail) Column {
                        MiniPlayer(player, vm::artworkUrl, ::openNow, vm.playback::togglePlay, { vm.playback.skip() })
                        if (!wide) NavigationBar {
                            destinations.forEach { destination ->
                                NavigationBarItem(selected = route == destination.route, onClick = { mainRoute(destination.route) },
                                    icon = { Icon(destination.icon, null) }, label = { Text(destination.label) })
                            }
                        }
                    }
                }) { padding ->
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                NavHost(modifier = Modifier.widthIn(max = if (route == "profile" || route == "lyrics") 760.dp else 1200.dp).fillMaxSize(), navController = nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(state.mixes, state.favorites, player.maturity, vm::artworkUrl,
                            { vm.playback.startRadio() }, { vm.playback.playMix(it) }, { vm.playback.playFavorite(it) }, padding,
                            onFavorites = { nav.navigate("favorites") }, onShare = vm::shareRadio,
                            onRefreshMixes = vm::refreshServerMixes, refreshingMixes = "mix-refresh" in state.busy, sharing = "shares" in state.busy,
                            recommendations = state.favoriteRecommendations, recommendationsLoading = state.favoriteRecommendationsLoading,
                            recommendationsError = state.favoriteRecommendationsError, onRefreshRecommendations = vm::loadFavoriteRecommendations,
                            onRecommendationTrack = { vm.playback.playTrack(it) }, onRecommendationArtist = { vm.playback.playArtist(it) },
                            onRecommendationAlbum = { artist, album -> vm.playback.playAlbum(artist, album) },
                            refreshing = state.libraryLoading, onRefresh = { vm.refreshContent() })
                    }
                    composable("library") {
                        LibraryScreen(state, player.ratings, player.ratingPending, player.sessionId != null, vm::artworkUrl, { vm.playback.playTrack(it) },
                            { vm.playback.playArtist(it) }, { a, al -> vm.playback.playAlbum(a, al) }, vm::toggleFavorite,
                            vm::toggleArtistFavorite, vm::toggleAlbumFavorite, vm.playback::rate, { addTrackId = it }, padding,
                            onFavoriteTrack = vm.playback::playFavorite, onRefresh = { vm.refreshContent() })
                    }
                    composable("favorites") {
                        LibraryScreen(state, player.ratings, player.ratingPending, player.sessionId != null, vm::artworkUrl, { vm.playback.playFavorite(it) },
                            { vm.playback.playArtist(it) }, { a, al -> vm.playback.playAlbum(a, al) }, vm::toggleFavorite,
                            vm::toggleArtistFavorite, vm::toggleAlbumFavorite, vm.playback::rate, { addTrackId = it }, padding, favoritesOnly = true,
                            onRefresh = { vm.refreshContent() })
                    }
                    composable("playlists") {
                        PlaylistsScreen(state, vm::artworkUrl, { nav.navigate("playlist/$it") }, { vm.openPlaylistEditor() }, vm::loadPlaylists, padding)
                    }
                    composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStack ->
                        val id = backStack.arguments!!.getLong("id")
                        LaunchedEffect(id) { vm.openPlaylist(id) }
                        DisposableEffect(id) { onDispose { vm.closePlaylist() } }
                        PlaylistDetailScreen(state, vm::artworkUrl, vm.playback::playPlaylist, vm::openPlaylistEditor, vm::deletePlaylist,
                            vm::removeFromPlaylist, vm::movePlaylistItem, { nav.navigate("pick/$id") }, { vm.openPlaylist(id) }, padding)
                        LaunchedEffect(state.playlistState.deletedId) {
                            if (state.playlistState.deletedId == id) {
                                vm.consumePlaylistDeletion(id)
                                nav.popBackStack()
                            }
                        }
                    }
                    composable("pick/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { stack ->
                        val id = stack.arguments!!.getLong("id")
                        LibraryScreen(state, player.ratings, player.ratingPending, player.sessionId != null, vm::artworkUrl, { vm.addToPlaylist(id, it) },
                            { }, { _, _ -> }, vm::toggleFavorite, vm::toggleArtistFavorite, vm::toggleAlbumFavorite,
                            vm.playback::rate, { vm.addToPlaylist(id, it) }, padding, selectionMode = true,
                            onRefresh = { vm.refreshContent() })
                    }
                    composable("now") {
                        NowPlayingScreen(player, vm::artworkUrl, vm.playback::togglePlay, { vm.playback.skip() },
                            vm.playback::dislike, { player.current?.let { vm.toggleFavorite(it.key) } }, vm.playback::seekTo,
                            { id, index -> if (player.fixed) vm.playback.jumpToIndex(index) else vm.playback.jumpTo(id) }, padding, onLike = vm.playback::like,
                            onLyrics = { nav.navigate("lyrics") }, onAddToPlaylist = { addTrackId = player.current?.key },
                            favoriteBusy = player.current?.let { "favorite:${it.key}" in state.busy } == true,
                            onPrevious = { vm.playback.previous() })
                    }
                    composable("lyrics") { LyricsScreen(app.api, player, vm.playback::seekTo, padding,
                        connectionKey = vm.savedUrl + "|" + vm.savedToken) }
                    composable("profile") {
                        LaunchedEffect(Unit) { vm.loadProfile() }
                        ProfileScreen(vm.savedUrl, state.serverTracks, state.serverVersion, player.maturity, state.profile,
                            state.favorites.size, vm::loadProfile, { vm.logout() }, padding,
                            onFavorites = { nav.navigate("favorites") }, state = state,
                            contextIds = player.contextIds, hasSession = player.sessionId != null && !player.fixed,
                            onExplore = vm::saveExplore, onCreateContext = vm::createContext,
                            onToggleContext = vm::toggleContext, onDeleteContext = vm::deleteContext,
                            onRestoreRule = vm::restoreRule, onCreateShare = { vm.createShare() },
                            onShare = { share ->
                                try { shareRadioLink(context, share.url) }
                                catch (e: Exception) { vm.showMessage(strings(R.string.main_activity_12)) }
                            }, onRevokeShare = vm::revokeShare)
                    }
                }
                }
            }
        }
    }
    LaunchedEffect(state.shareToSend) {
        state.shareToSend?.let { share ->
            try { shareRadioLink(context, share.url) }
            catch (e: Exception) { vm.showMessage(strings(R.string.main_activity_12)) }
            finally { vm.consumeShare() }
        }
    }
    state.playlistState.editor?.let { draft ->
        PlaylistEditor(draft, state.playlistState.busy, vm::changePlaylistName,
            vm::changePlaylistDescription, vm::dismissPlaylistEditor, vm::savePlaylistEditor)
    }
    if (addTrackId != null) AddToPlaylistSheet(state.playlists, "playlist-mutation" in state.busy,
        onSelect = { id -> addTrackId?.let { vm.addToPlaylist(id, it) }; addTrackId = null },
        onCreate = { addTrackId = null; mainRoute("playlists") }, onDismiss = { addTrackId = null })
}
