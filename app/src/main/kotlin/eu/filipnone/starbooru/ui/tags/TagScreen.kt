package eu.filipnone.starbooru.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.Tag
import eu.filipnone.starbooru.core.TagCategory
import eu.filipnone.starbooru.ui.common.LocalNotices
import eu.filipnone.starbooru.ui.common.describe
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.theme.StarBooruTheme

/**
 * A tag: how many posts carry it and a way to them, its category, where it
 * sits in the tree, its aliases and what it implies. Administrators change
 * them here; everyone else reads them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagScreen(
    vm: TagViewModel,
    server: String,
    admin: Boolean,
    onSearch: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val news by vm.news.collectAsStateWithLifecycle()
    val notices = LocalNotices.current
    val res = LocalResources.current
    val recolouredWords = news.let { n -> if (n is TagNews.Recoloured) categoryName(n.category) else null }
    LaunchedEffect(news) {
        when (val n = news) {
            is TagNews.Recoloured -> notices?.say(
                res.getString(R.string.tags_recoloured, n.name.replace('_', ' '), recolouredWords.orEmpty()),
            )

            is TagNews.Failed -> notices?.say(res.getString(R.string.tags_recolour_failed, describe(n.error, res, server)))

            null -> return@LaunchedEffect
        }
        vm.told()
    }
    val frame = LocalFrame.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(frame.padding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val detail = state.detail
            val error = state.error
            if (detail == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (error != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(describe(error, server), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalButton(onClick = vm::load, modifier = Modifier.padding(top = 12.dp)) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }
                return@Column
            }
            val tag = detail.tag
            Text(
                tag.name.replace('_', ' '),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = categoryColor(tag.category),
            )
            if (tag.name !=
                vm.name
            ) {
                Text(stringResource(R.string.tags_is_alias, vm.name), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { onSearch(tag.name) }) {
                Icon(painterResource(R.drawable.ic_gallery), null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(pluralStringResource(R.plurals.tags_browse, tag.postCount, tag.postCount))
            }
            if (!admin) {
                Text(
                    stringResource(R.string.tags_admins_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Card(stringResource(R.string.tags_category)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagCategory.entries.forEach { c ->
                        FilterChip(
                            selected = tag.category == c,
                            enabled = admin && !state.busy,
                            onClick = { if (tag.category != c) vm.setCategory(c) },
                            label = { Text(categoryName(c), color = categoryColor(c), fontWeight = FontWeight.SemiBold) },
                        )
                    }
                }
            }

            Card(stringResource(R.string.tags_tree), stringResource(R.string.tags_tree_hint)) {
                if (detail.ancestors.isEmpty()) {
                    Muted(stringResource(R.string.tags_tree_top))
                } else {
                    FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        detail.ancestors.forEach { a ->
                            Chip(a, onTag)
                            Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(18.dp))
                        }
                        Text(tag.name.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                    }
                }
                if (admin) {
                    AddRow(stringResource(R.string.tags_parent), stringResource(R.string.tags_move), state.busy) { vm.setParent(it) }
                    if (detail.ancestors.isNotEmpty()) {
                        FilledTonalButton(onClick = {
                            vm.setParent("")
                        }, enabled = !state.busy) { Text(stringResource(R.string.tags_move_top)) }
                    }
                }
                Label(stringResource(R.string.tags_children))
                Tags(detail.children, stringResource(R.string.tags_children_none), onTag)
            }

            Card(stringResource(R.string.tags_aliases), stringResource(R.string.tags_aliases_hint)) {
                if (detail.aliases.isEmpty()) Muted(stringResource(R.string.tags_aliases_none))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    detail.aliases.forEach { alias -> Removable(alias, admin && !state.busy) { vm.removeAlias(alias) } }
                }
                if (admin) {
                    AddRow(stringResource(R.string.tags_new_alias), stringResource(R.string.tags_merge), state.busy) {
                        vm.addAlias(it)
                    }
                }
            }

            Card(stringResource(R.string.tags_implies), stringResource(R.string.tags_implies_hint)) {
                if (detail.implies.isEmpty()) Muted(stringResource(R.string.tags_implies_none))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    detail.implies.forEach { t ->
                        Removable(t.name, admin && !state.busy, categoryColor(t.category)) { vm.removeImplication(t.name) }
                    }
                }
                if (admin) {
                    AddRow(stringResource(R.string.tags_implied_tag), stringResource(R.string.tags_implies), state.busy) {
                        vm.addImplication(it)
                    }
                }
            }

            Card(stringResource(R.string.tags_implied_by), stringResource(R.string.tags_implied_by_hint)) {
                Tags(detail.impliedBy, stringResource(R.string.tags_implied_by_none), onTag)
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Card(
    title: String,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(StarBooruTheme.roles.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        hint?.let { Muted(it) }
        content()
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(
    tags: List<Tag>,
    none: String,
    onTag: (String) -> Unit,
) {
    if (tags.isEmpty()) {
        Muted(none)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { Chip(it, onTag) }
    }
}

@Composable
private fun Chip(
    tag: Tag,
    onTag: (String) -> Unit,
) {
    val roles = StarBooruTheme.roles
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(roles.surface2)
            .border(1.dp, roles.outline, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) { onTag(tag.name) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(tag.name.replace('_', ' '), color = categoryColor(tag.category), fontWeight = FontWeight.Medium)
    }
}

/** A name with a button that takes it off. */
@Composable
private fun Removable(
    name: String,
    can: Boolean,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onRemove: () -> Unit,
) {
    val roles = StarBooruTheme.roles
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(roles.surface2)
            .border(1.dp, roles.outline, RoundedCornerShape(8.dp))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name.replace('_', ' '), color = color, fontWeight = FontWeight.Medium)
        if (can) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.tags_remove_named, name), Modifier.size(16.dp))
            }
        } else {
            Spacer(Modifier.size(12.dp))
        }
    }
}

/** A field and its button, for a name to add. */
@Composable
private fun AddRow(
    label: String,
    action: String,
    busy: Boolean,
    onAdd: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val go = {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { go() }),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = go, enabled = !busy && text.isNotBlank(), shape = CircleShape) { Text(action) }
    }
}
