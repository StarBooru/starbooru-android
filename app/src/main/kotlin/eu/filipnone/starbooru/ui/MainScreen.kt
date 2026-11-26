package eu.filipnone.starbooru.ui

import android.provider.Settings.Global
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import eu.filipnone.starbooru.AppGraph
import eu.filipnone.starbooru.core.Mode
import eu.filipnone.starbooru.core.trashQuery
import eu.filipnone.starbooru.data.AccountState
import eu.filipnone.starbooru.data.Settings
import eu.filipnone.starbooru.ui.common.LocalNotices
import eu.filipnone.starbooru.ui.common.LocalReducedMotion
import eu.filipnone.starbooru.ui.common.LocalSharedTransition
import eu.filipnone.starbooru.ui.common.LocalViewerFocus
import eu.filipnone.starbooru.ui.common.Notices
import eu.filipnone.starbooru.ui.common.Soon
import eu.filipnone.starbooru.ui.explore.ExploreScreen
import eu.filipnone.starbooru.ui.explore.ExploreViewModel
import eu.filipnone.starbooru.ui.frame.Frame
import eu.filipnone.starbooru.ui.frame.LocalBars
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.gallery.GalleryScreen
import eu.filipnone.starbooru.ui.gallery.GalleryViewModel
import eu.filipnone.starbooru.ui.nav.ExploreRoute
import eu.filipnone.starbooru.ui.nav.ForumRoute
import eu.filipnone.starbooru.ui.nav.GalleryRoute
import eu.filipnone.starbooru.ui.nav.PostRoute
import eu.filipnone.starbooru.ui.nav.SettingsRoute
import eu.filipnone.starbooru.ui.nav.Tab
import eu.filipnone.starbooru.ui.nav.TagRoute
import eu.filipnone.starbooru.ui.nav.TagsRoute
import eu.filipnone.starbooru.ui.nav.TrashRoute
import eu.filipnone.starbooru.ui.nav.UploadRoute
import eu.filipnone.starbooru.ui.nav.entries
import eu.filipnone.starbooru.ui.nav.rememberNavigationState
import eu.filipnone.starbooru.ui.settings.SettingsScreen
import eu.filipnone.starbooru.ui.signin.SignInScreen
import eu.filipnone.starbooru.ui.signin.SignInViewModel
import eu.filipnone.starbooru.ui.tags.TagScreen
import eu.filipnone.starbooru.ui.tags.TagViewModel
import eu.filipnone.starbooru.ui.tags.TagsScreen
import eu.filipnone.starbooru.ui.tags.TagsViewModel
import eu.filipnone.starbooru.ui.trash.TrashScreen
import eu.filipnone.starbooru.ui.trash.TrashViewModel
import eu.filipnone.starbooru.ui.viewer.ViewerScreen
import eu.filipnone.starbooru.ui.viewer.ViewerViewModel
import kotlinx.coroutines.launch

/** Sign-in, or the frame and its pages, as the session says. */
@Composable
fun MainScreen(
    graph: AppGraph,
    settings: Settings,
) {
    val account by graph.account.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The system's Remove animations sets the animator scale to 0.
    val reduced =
        remember {
            Global.getFloat(context.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
    // The surface gives everything under it the right ink, in light and dark.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CompositionLocalProvider(LocalReducedMotion provides reduced) {
            when (val a = account) {
                AccountState.Starting -> {}

                is AccountState.SignedOut -> {
                    val vm = viewModel(key = "signin") { SignInViewModel(graph.account, graph.apis, createSavedStateHandle()) }
                    LaunchedEffect(a) { vm.arrive(a.lastServer, a.expired) }
                    SignInScreen(vm)
                }

                is AccountState.Guest, is AccountState.SignedIn -> Shell(graph, settings, a)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Shell(
    graph: AppGraph,
    settings: Settings,
    account: AccountState,
) {
    val nav = rememberNavigationState()
    val info by graph.account.info.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val server = (account as? AccountState.SignedIn)?.server ?: (account as? AccountState.Guest)?.server.orEmpty()
    val guest = account is AccountState.Guest
    val public = info?.mode == Mode.PUBLIC
    val tabs = Tab.shown(public, guest)
    val focus = remember { mutableStateOf<Long?>(null) }
    val bars = remember { mutableStateOf(true) }
    val snack = remember { SnackbarHostState() }
    val notices = remember { Notices(snack, scope) }
    val current = nav.current
    var search by rememberSaveable { mutableStateOf("") }
    var exploreQuery by rememberSaveable { mutableStateOf("") }
    // Whose history the viewer adds to: one list per account on each server.
    val owner =
        when (account) {
            is AccountState.SignedIn -> "${account.server}#${account.user.id}"
            is AccountState.Guest -> "${account.server}#guest"
            else -> ""
        }
    LaunchedEffect(current) {
        when (current) {
            is GalleryRoute -> search = current.query
            is PostRoute -> search = current.query
            ExploreRoute -> search = exploreQuery
            else -> {}
        }
    }
    // A tab the server no longer shows (the forum after a switch to private)
    // falls back to the gallery.
    LaunchedEffect(tabs) { if (nav.tab !in tabs) nav.select(Tab.Gallery) }
    val update: ((Settings) -> Settings) -> Unit = { change -> scope.launch { graph.settings.update(change) } }
    val searchFor: (String) -> Unit = searchFor@{ q ->
        val query = q.trim()
        // On Explore, the search narrows Explore.
        if (nav.tab == Tab.Explore && nav.current == ExploreRoute) {
            exploreQuery = query
            return@searchFor
        }
        if (nav.tab != Tab.Gallery) nav.select(Tab.Gallery)
        val top = nav.current
        if (top is GalleryRoute && top.query == query) return@searchFor
        nav.open(GalleryRoute(query))
    }

    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransition provides this,
            LocalViewerFocus provides focus,
            LocalBars provides bars,
            LocalNotices provides notices,
        ) {
            Frame(
                tabs = tabs,
                selected = nav.tab.takeIf { it in tabs },
                onSelect = nav::select,
                bars = bars.value,
                hand = settings.hand,
                search = search,
                onSearch = { search = it },
                onSubmit = searchFor,
                onBrand = { nav.select(Tab.Gallery) },
                onSignIn = if (guest) ({ scope.launch { graph.account.toSignIn() } }) else null,
            ) {
                val provider =
                    entryProvider<NavKey> {
                        entry<GalleryRoute> { route ->
                            val vm = viewModel { GalleryViewModel(graph.account, route.query, createSavedStateHandle()) }
                            GalleryScreen(
                                vm = vm,
                                account = graph.account,
                                server = server,
                                settings = settings,
                                onSettings = update,
                                onOpen = { post -> nav.open(PostRoute(post.id, vm.query, vm.seed)) },
                                onSearch = { q -> nav.replace(GalleryRoute(q)) },
                            )
                        }
                        entry<PostRoute> { route ->
                            val vm =
                                viewModel {
                                    ViewerViewModel(graph.account, route.id, route.query, route.seed, createSavedStateHandle()) { id ->
                                        graph.scope.launch { graph.history.open(owner, id) }
                                    }
                                }
                            ViewerScreen(
                                vm = vm,
                                account = graph.account,
                                server = server,
                                onOpen = { post -> nav.open(PostRoute(post.id)) },
                                onTag = { nav.open(TagRoute(it)) },
                                onBack = { nav.back() },
                                onSearch = searchFor,
                            )
                        }
                        entry<ExploreRoute> {
                            val vm = viewModel { ExploreViewModel(graph.account, graph.history, createSavedStateHandle()) }
                            ExploreScreen(
                                vm = vm,
                                server = server,
                                mode = info?.mode,
                                query = exploreQuery,
                                onOpen = { post -> nav.open(PostRoute(post.id)) },
                                onSearch = { q ->
                                    nav.select(Tab.Gallery)
                                    nav.open(GalleryRoute(q))
                                },
                            )
                        }
                        entry<TagsRoute> {
                            val vm = viewModel { TagsViewModel(graph.account, createSavedStateHandle()) }
                            TagsScreen(vm, server, onTag = { nav.open(TagRoute(it)) })
                        }
                        entry<TagRoute> { route ->
                            val vm = viewModel { TagViewModel(graph.account, route.name) }
                            TagScreen(
                                vm = vm,
                                server = server,
                                admin = (account as? AccountState.SignedIn)?.user?.admin == true,
                                onSearch = searchFor,
                                onTag = { nav.open(TagRoute(it)) },
                            )
                        }
                        entry<UploadRoute> { Soon(Tab.Upload.icon, stringResource(Tab.Upload.label)) }
                        entry<ForumRoute> { Soon(Tab.Forum.icon, stringResource(Tab.Forum.label)) }
                        entry<SettingsRoute> {
                            SettingsScreen(
                                settings = settings,
                                account = account,
                                serverVersion = info?.version,
                                onChange = update,
                                onSignOut = { scope.launch { graph.account.signOut() } },
                                onSignIn = { scope.launch { graph.account.toSignIn() } },
                                onTrash = { nav.open(TrashRoute) },
                            )
                        }
                        entry<TrashRoute> {
                            val vm = viewModel { TrashViewModel(graph.account) }
                            TrashScreen(
                                vm = vm,
                                server = server,
                                admin = (account as? AccountState.SignedIn)?.user?.admin == true,
                                onOpen = { post -> nav.open(PostRoute(post.id, trashQuery())) },
                            )
                        }
                    }
                NavDisplay(
                    entries = nav.entries(provider),
                    sharedTransitionScope = this@SharedTransitionLayout,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(90)) },
                    popTransitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(90)) },
                    predictivePopTransitionSpec = { fadeIn(tween(220)) togetherWith (fadeOut(tween(220)) + scaleOut(targetScale = 0.9f)) },
                    onBack = { nav.back() },
                )
                Box(
                    Modifier.fillMaxSize().padding(bottom = LocalFrame.current.padding.calculateBottomPadding()),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    SnackbarHost(snack)
                }
            }
        }
    }
}
