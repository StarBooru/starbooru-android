package eu.filipnone.starbooru.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.MediaKind
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.Rating
import eu.filipnone.starbooru.core.TagCategory
import eu.filipnone.starbooru.core.TagGroup
import eu.filipnone.starbooru.core.TagGroupKind
import eu.filipnone.starbooru.core.colorQuery
import eu.filipnone.starbooru.core.dimensions
import eu.filipnone.starbooru.core.fileDateText
import eu.filipnone.starbooru.core.formatDuration
import eu.filipnone.starbooru.core.groupTags
import eu.filipnone.starbooru.core.sourceUrl
import eu.filipnone.starbooru.core.tagGroupsHeaded
import eu.filipnone.starbooru.core.timeText
import eu.filipnone.starbooru.ui.common.fileSize
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import java.util.TimeZone

/** The cards beside or under the picture: its tags, then the file's details. */
@Composable
fun PostDetails(
    post: Post,
    onSearch: (String) -> Unit,
    onTag: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TagsCard(post, onSearch, onTag)
        DetailsCard(post, onSearch)
    }
}

@Composable
private fun Card(
    title: String,
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
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsCard(
    post: Post,
    onSearch: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    val groups = remember(post.tagDetails, post.tags) { groupTags(post.tagDetails) }
    Card(stringResource(R.string.post_tags)) {
        if (post.tags.isEmpty()) {
            Text(stringResource(R.string.post_no_tags), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (groups.isEmpty()) {
            // A list from an older server, without categories.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                post.tags.forEach { TagChip(it, TagCategory.GENERAL, onSearch, onTag) }
            }
        } else {
            val headed = tagGroupsHeaded(groups)
            groups.forEach { group ->
                if (headed) {
                    Text(
                        groupTitle(group).uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.tags.forEach { TagChip(it.name, it.category, onSearch, onTag) }
                }
            }
        }
    }
}

@Composable
private fun groupTitle(group: TagGroup): String =
    when (group.kind) {
        TagGroupKind.ARTIST -> stringResource(R.string.category_artist)

        TagGroupKind.COPYRIGHT -> stringResource(R.string.category_copyright)

        TagGroupKind.CHARACTER -> stringResource(R.string.category_character)

        TagGroupKind.META -> stringResource(R.string.category_meta)

        TagGroupKind.OTHER -> stringResource(R.string.section_other)

        TagGroupKind.SECTION ->
            when (group.section) {
                "people" -> stringResource(R.string.section_people)
                "hair" -> stringResource(R.string.section_hair)
                "eyes" -> stringResource(R.string.section_eyes)
                "skin" -> stringResource(R.string.section_skin)
                "body" -> stringResource(R.string.section_body)
                "expression" -> stringResource(R.string.section_expression)
                "pose" -> stringResource(R.string.section_pose)
                "clothing" -> stringResource(R.string.section_clothing)
                "setting" -> stringResource(R.string.section_setting)
                "style" -> stringResource(R.string.section_style)
                else -> group.section.orEmpty().replace('_', ' ')
            }
    }

@Composable
private fun TagChip(
    name: String,
    category: TagCategory,
    onSearch: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    val roles = StarBooruTheme.roles
    val colour =
        when (category) {
            TagCategory.ARTIST -> roles.catArtist
            TagCategory.CHARACTER -> roles.catCharacter
            TagCategory.COPYRIGHT -> roles.catCopyright
            TagCategory.META -> roles.catMeta
            TagCategory.GENERAL -> MaterialTheme.colorScheme.onSurface
        }
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(roles.surface2)
            .border(1.dp, roles.outline, RoundedCornerShape(8.dp))
            // A tap searches the tag; a long press opens its page.
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.tag_menu_details),
                onLongClick = { onTag(name) },
            ) { onSearch(name) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.replace('_', ' '),
            color = colour,
            fontSize = 14.sp,
            fontWeight = if (category == TagCategory.GENERAL) null else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsCard(
    post: Post,
    onSearch: (String) -> Unit,
) {
    val uri = LocalUriHandler.current
    val zone = remember { TimeZone.getDefault() }
    fun offset(at: Long) = zone.getOffset(at) / 1000
    Card(stringResource(R.string.post_details)) {
        Row(stringResource(R.string.rating), ratingName(post.rating))
        Row(stringResource(R.string.post_size), fileSize(post.fileSize))
        Row(stringResource(R.string.post_dimensions), dimensions(post.width, post.height))
        if (post.kind == MediaKind.VIDEO) post.durationMs?.let { formatDuration(it) }?.let { Row(stringResource(R.string.post_length), it) }
        Row(stringResource(R.string.post_type), post.mime)
        post.fileDateMs?.let { fileDateText(post.kind, it, offset(it)) }?.let { Row(stringResource(R.string.post_file_date), it) }
        if (post.palette.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.post_colors))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.palette.forEach { hex ->
                        val colour = parse(hex) ?: return@forEach
                        val label = stringResource(R.string.post_color_search, hex)
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(colour)
                                .border(1.dp, StarBooruTheme.roles.outline, CircleShape)
                                .semantics { contentDescription = label }
                                .clickable(role = Role.Button) { onSearch(colorQuery(hex)) },
                        )
                    }
                }
            }
        }
        timeText(post.createdAtMs, offset(post.createdAtMs))?.let { Row(stringResource(R.string.post_added), it) }
        post.source?.takeIf { it.isNotBlank() }?.let { source ->
            val link = sourceUrl(source)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Label(stringResource(R.string.post_source))
                Text(
                    source,
                    color = if (link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.weight(1f).then(
                            if (link != null) Modifier.clickable { runCatching { uri.openUri(link) } } else Modifier,
                        ),
                )
            }
        }
        if (post.sampleUrl != null) {
            Text(
                stringResource(R.string.post_sample_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
}

@Composable
private fun Row(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth()) {
        Label(label)
        Text(value, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ratingName(rating: Rating): String =
    stringResource(
        when (rating) {
            Rating.SAFE -> R.string.rating_safe
            Rating.SENSITIVE -> R.string.rating_sensitive
            Rating.QUESTIONABLE -> R.string.rating_questionable
            Rating.EXPLICIT -> R.string.rating_explicit
        },
    )

private fun parse(hex: String): Color? {
    val h = hex.removePrefix("#")
    if (h.length != 6) return null
    return h.toLongOrNull(16)?.let { Color(0xFF000000L or it) }
}
