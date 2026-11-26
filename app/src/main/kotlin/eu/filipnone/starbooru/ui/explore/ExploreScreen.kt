package eu.filipnone.starbooru.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.Mode
import eu.filipnone.starbooru.core.Person
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.TagTrend
import eu.filipnone.starbooru.ui.common.LocalNotices
import eu.filipnone.starbooru.ui.common.Media
import eu.filipnone.starbooru.ui.common.describe
import eu.filipnone.starbooru.ui.common.tintColor
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.tags.categoryColor
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import java.time.Instant
import java.time.ZoneOffset

/** The windows a public server's Trending offers, in days, and their names. */
private val WINDOWS = listOf(
    1 to R.string.explore_today,
    7 to R.string.explore_week,
    30 to R.string.explore_month,
    365 to R.string.explore_year,
)

@Composable
fun ExploreScreen(
    vm: ExploreViewModel,
    server: String,
    mode: Mode?,
    query: String,
    onOpen: (Post) -> Unit,
    onSearch: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(query) { vm.setQuery(query) }
    val notices = LocalNotices.current
    val res = LocalResources.current
    val frame = LocalFrame.current
    val public = mode == Mode.PUBLIC
    val explore = state.explore
    val phrase = phrase(state.days)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(frame.padding)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            if (state.query.isBlank()) stringResource(R.string.nav_explore) else state.query,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val error = state.error
        if (error != null && explore == null) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(describe(error, server), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = vm::load, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.retry)) }
            }
        }
        if (explore == null && state.loading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        if (public && explore != null) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.explore_window), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    WINDOWS.forEachIndexed { i, (days, label) ->
                        SegmentedButton(
                            selected = state.days == days,
                            onClick = { vm.setDays(days) },
                            shape = SegmentedButtonDefaults.itemShape(i, WINDOWS.size),
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
            }
            Section(stringResource(R.string.explore_trending)) {
                if (explore.trending.isEmpty()) {
                    Muted(
                        stringResource(R.string.explore_no_likes, phrase),
                    )
                } else {
                    Posts(explore.trending, onOpen)
                }
            }
            Section(stringResource(R.string.explore_popular_tags), note = stringResource(R.string.explore_tags_note, phrase)) {
                if (explore.tags.isEmpty()) Muted(stringResource(R.string.explore_no_tags, phrase)) else Trends(explore.tags, onSearch)
            }
            Section(stringResource(R.string.explore_popular_characters), note = stringResource(R.string.explore_characters_note, phrase)) {
                if (explore.popularCharacters.isEmpty()) {
                    Muted(
                        stringResource(R.string.explore_no_characters, phrase),
                    )
                } else {
                    People(explore.popularCharacters, onSearch)
                }
            }
        }
        if (state.recent.isNotEmpty()) {
            Section(
                stringResource(R.string.explore_recent),
                action = {
                    TextButton(onClick = {
                        vm.clearRecent { undo ->
                            notices?.offer(res.getString(R.string.explore_recent_cleared), res.getString(R.string.press_undo), undo)
                        }
                    }) { Text(stringResource(R.string.explore_recent_clear)) }
                },
            ) {
                Posts(state.recent, onOpen, more = if (state.recent.size < state.recentCount) ({ vm.loadRecent() }) else null)
            }
        }
        if (explore != null) {
            Section(stringResource(R.string.explore_on_this_day)) {
                if (explore.onThisDay.isEmpty()) {
                    Muted(stringResource(R.string.explore_no_memories))
                } else {
                    val now = remember { Instant.now().atZone(ZoneOffset.UTC).year }
                    Posts(explore.onThisDay, onOpen) { post ->
                        val years = now - Instant.ofEpochMilli(post.createdAtMs).atZone(ZoneOffset.UTC).year
                        if (years > 0) pluralStringResource(R.plurals.explore_years_ago, years, years) else null
                    }
                }
            }
            Section(
                stringResource(R.string.explore_random),
                action = {
                    TextButton(onClick = vm::shuffle, enabled = !state.shuffling) {
                        Icon(painterResource(R.drawable.ic_shuffle), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.explore_shuffle))
                    }
                },
            ) {
                if (state.random.isEmpty()) Muted(stringResource(R.string.explore_nothing_body)) else Posts(state.random, onOpen)
            }
            if (explore.people.isNotEmpty()) {
                Section(stringResource(R.string.explore_people)) { People(explore.people, onSearch) }
            }
            val empty = explore.random.isEmpty() && explore.onThisDay.isEmpty() && explore.trending.isEmpty() && state.recent.isEmpty()
            if (empty && state.query.isBlank()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.ic_explore),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        stringResource(R.string.explore_nothing),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Muted(stringResource(R.string.explore_nothing_body))
                }
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun phrase(days: Int): String =
    when (days) {
        1 -> stringResource(R.string.explore_phrase_today)
        7 -> stringResource(R.string.explore_phrase_week)
        30 -> stringResource(R.string.explore_phrase_month)
        365 -> stringResource(R.string.explore_phrase_year)
        else -> pluralStringResource(R.plurals.explore_phrase_days, days, days)
    }

@Composable
private fun Section(
    title: String,
    note: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics {
                        heading()
                    },
                )
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
}

/** A row of posts to run a thumb along, with an optional line under each. */
@Composable
private fun Posts(
    posts: List<Post>,
    onOpen: (Post) -> Unit,
    more: (() -> Unit)? = null,
    caption: @Composable (Post) -> String? = { null },
) {
    val context = LocalContext.current
    val roles = StarBooruTheme.roles
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(posts, key = { it.id }) { post ->
            val label = stringResource(R.string.post_alt, post.id.toString())
            Column(Modifier.width(140.dp)) {
                Box(
                    Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(post.tintColor() ?: roles.surface2)
                        .semantics { contentDescription = label }
                        .clickable { onOpen(post) },
                ) {
                    Media.thumb(context, post)?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                caption(post)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
            }
        }
        if (more != null) {
            item {
                Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                    FilledTonalButton(onClick = more) { Text(stringResource(R.string.load_more)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Trends(
    trends: List<TagTrend>,
    onSearch: (String) -> Unit,
) {
    val roles = StarBooruTheme.roles
    FlowRow(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        trends.forEach { trend ->
            Row(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(roles.surface2)
                    .border(1.dp, roles.outline, RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { onSearch(trend.tag.name) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(trend.tag.name.replace('_', ' '), color = categoryColor(trend.tag.category), fontWeight = FontWeight.Medium)
                Text(
                    "+${trend.recent}",
                    color = roles.ok,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/** People as round faces with their names, a tap away from their posts. */
@Composable
private fun People(
    people: List<Person>,
    onSearch: (String) -> Unit,
) {
    val roles = StarBooruTheme.roles
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(people, key = { it.name }) { person ->
            Column(
                Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSearch(person.name) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(roles.surface2)) {
                    (person.faceUrl ?: person.thumbUrl)?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    person.name.replace('_', ' '),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
