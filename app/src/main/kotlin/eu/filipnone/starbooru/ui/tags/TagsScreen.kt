package eu.filipnone.starbooru.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.Tag
import eu.filipnone.starbooru.core.TagCategory
import eu.filipnone.starbooru.core.TagOrder
import eu.filipnone.starbooru.ui.common.describe
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import java.text.NumberFormat
import kotlinx.coroutines.flow.distinctUntilChanged

/** The colour a tag's name takes for its category; general tags take the text's. */
@Composable
fun categoryColor(category: TagCategory): Color {
    val roles = StarBooruTheme.roles
    return when (category) {
        TagCategory.ARTIST -> roles.catArtist
        TagCategory.CHARACTER -> roles.catCharacter
        TagCategory.COPYRIGHT -> roles.catCopyright
        TagCategory.META -> roles.catMeta
        TagCategory.GENERAL -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
fun categoryName(category: TagCategory): String =
    stringResource(
        when (category) {
            TagCategory.GENERAL -> R.string.category_general
            TagCategory.ARTIST -> R.string.category_artist
            TagCategory.CHARACTER -> R.string.category_character
            TagCategory.COPYRIGHT -> R.string.category_copyright
            TagCategory.META -> R.string.category_meta
        },
    )

/** Every tag in use, as cards in their category's colour with how many posts carry them. */
@Composable
fun TagsScreen(
    vm: TagsViewModel,
    server: String,
    onTag: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val frame = LocalFrame.current
    val dir = LocalLayoutDirection.current
    val grid = rememberLazyGridState()
    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= grid.layoutInfo.totalItemsCount - 20) vm.loadMore() }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
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
        item(span = { GridItemSpan(maxLineSpan) }) { Head(state, vm) }
        items(state.shown, key = { it.id }) { tag -> TagCard(tag) { onTag(tag.name) } }
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

                    state.loaded && state.shown.isEmpty() ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painterResource(R.drawable.ic_label),
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            )
                            val none = state.filter.isBlank() && state.category == null
                            Text(
                                stringResource(if (none) R.string.tags_none_yet else R.string.tags_none_match),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            if (none) Text(stringResource(R.string.tags_appear), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                }
            }
        }
    }
}

@Composable
private fun Head(
    state: TagsState,
    vm: TagsViewModel,
) {
    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.nav_tags), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.filter,
            onValueChange = vm::setFilter,
            placeholder = { Text(stringResource(R.string.tags_filter_placeholder)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val orders = listOf(TagOrder.COUNT to R.string.tags_most_used, TagOrder.NAME to R.string.tags_a_z)
            orders.forEachIndexed { i, (order, label) ->
                SegmentedButton(
                    selected = state.order == order,
                    onClick = { vm.setOrder(order) },
                    shape = SegmentedButtonDefaults.itemShape(i, orders.size),
                ) { Text(stringResource(label)) }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = state.category == null, onClick = {
                    vm.setCategory(null)
                }, label = { Text(stringResource(R.string.tags_any_category)) })
            }
            items(TagCategory.entries) { c ->
                FilterChip(
                    selected = state.category == c,
                    onClick = { vm.setCategory(if (state.category == c) null else c) },
                    label = { Text(categoryName(c), color = categoryColor(c), fontWeight = FontWeight.SemiBold) },
                )
            }
        }
    }
}

@Composable
private fun TagCard(
    tag: Tag,
    onClick: () -> Unit,
) {
    val roles = StarBooruTheme.roles
    val count = tag.postCount
    val shown = NumberFormat.getIntegerInstance().format(count)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(roles.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                tag.name.replace('_', ' '),
                color = categoryColor(tag.category),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.tags_posts, count, count, shown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
