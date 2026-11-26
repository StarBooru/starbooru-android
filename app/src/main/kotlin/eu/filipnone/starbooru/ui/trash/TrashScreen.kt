package eu.filipnone.starbooru.ui.trash

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.ui.common.LocalNotices
import eu.filipnone.starbooru.ui.common.Media
import eu.filipnone.starbooru.ui.common.describe
import eu.filipnone.starbooru.ui.common.tintColor
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The recycle bin: each post with Restore and Delete for good, the whole bin
 * restored at once, or emptied by an administrator. What can't be undone
 * asks a second tap.
 */
@Composable
fun TrashScreen(
    vm: TrashViewModel,
    server: String,
    admin: Boolean,
    onOpen: (Post) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val news by vm.news.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val res = LocalResources.current
    val frame = LocalFrame.current
    val dir = LocalLayoutDirection.current
    LaunchedEffect(news) {
        val n = news ?: return@LaunchedEffect
        notices?.say(
            when (n) {
                is TrashNews.Restored -> res.getQuantityString(R.plurals.trash_restored, n.count, n.count)

                is TrashNews.Purged -> res.getQuantityString(R.plurals.trash_purged, n.count, n.count)

                is TrashNews.Failed ->
                    res.getString(
                        if (n.restoring) R.string.trash_restore_failed else R.string.trash_purge_failed,
                        n.id?.toString().orEmpty(),
                        describe(n.error, res, server),
                    )
            },
        )
        vm.told()
    }
    val grid = rememberLazyGridState()
    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= grid.layoutInfo.totalItemsCount - 12) vm.loadMore() }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = grid,
        contentPadding =
            PaddingValues(
                start = frame.padding.calculateStartPadding(dir) + 12.dp,
                end = frame.padding.calculateEndPadding(dir) + 12.dp,
                top = frame.padding.calculateTopPadding(),
                bottom = frame.padding.calculateBottomPadding() + 16.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Head(state, admin, vm::restoreAll, vm::empty)
        }
        items(state.posts, key = { it.id }) { post ->
            Item(post, busy = post.id in state.busy, onOpen = {
                onOpen(post)
            }, onRestore = { vm.restore(post) }, onPurge = { vm.purge(post) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(16.dp), contentAlignment = Alignment.Center) {
                val error = state.error
                when {
                    error != null ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(describe(error, server), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalButton(onClick = vm::loadMore, modifier = Modifier.padding(top = 12.dp)) {
                                Text(stringResource(R.string.retry))
                            }
                        }

                    state.loading -> CircularProgressIndicator()

                    state.loaded && state.posts.isEmpty() ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                stringResource(R.string.trash_none),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Head(
    state: TrashState,
    admin: Boolean,
    onRestoreAll: () -> Unit,
    onEmpty: () -> Unit,
) {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.trash_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.trash_lead), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.posts.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Confirm(
                    stringResource(R.string.trash_restore_all),
                    stringResource(R.string.trash_restore_all_confirm),
                    danger = false,
                    onRestoreAll,
                )
                if (admin) {
                    Confirm(
                        stringResource(R.string.trash_empty),
                        stringResource(R.string.trash_empty_confirm),
                        danger = true,
                        onEmpty,
                    )
                }
            }
        }
    }
}

/** A button that asks once more before it acts. */
@Composable
private fun Confirm(
    label: String,
    sure: String,
    danger: Boolean,
    onConfirm: () -> Unit,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    val roles = StarBooruTheme.roles
    FilledTonalButton(
        onClick = {
            if (asking) onConfirm()
            asking = !asking
        },
        colors =
            if (danger && asking) {
                androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = roles.dangerContainer,
                    contentColor = roles.onDangerContainer,
                )
            } else {
                androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
            },
    ) {
        AnimatedContent(asking, label = "confirm") { a -> Text(if (a) sure else label) }
    }
    if (asking) TextButton(onClick = { asking = false }) { Text(stringResource(R.string.notice_close)) }
}

@Composable
private fun Item(
    post: Post,
    busy: Boolean,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    val context = LocalContext.current
    val roles = StarBooruTheme.roles
    var asking by remember { mutableStateOf(false) }
    // A paler picture: these are on their way out.
    val pale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.6f) }) }
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(roles.surface),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(post.tintColor() ?: roles.surface2)
                .clickable(onClick = onOpen),
        ) {
            Media.thumb(context, post)?.let {
                AsyncImage(
                    model = it,
                    contentDescription = stringResource(R.string.post_alt, post.id.toString()),
                    contentScale = ContentScale.Crop,
                    colorFilter = pale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (asking) {
                TextButton(onClick = { asking = false }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.notice_close), maxLines = 1)
                }
                TextButton(
                    onClick = {
                        asking = false
                        onPurge()
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.trash_purge), color = roles.danger, maxLines = 1, fontWeight = FontWeight.SemiBold) }
            } else {
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !busy, onClick = onRestore),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_restore), stringResource(R.string.trash_restore_post, post.id.toString()))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !busy) { asking = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_delete_forever),
                        stringResource(R.string.trash_purge_post, post.id.toString()),
                        tint = roles.danger,
                    )
                }
            }
        }
    }
}
