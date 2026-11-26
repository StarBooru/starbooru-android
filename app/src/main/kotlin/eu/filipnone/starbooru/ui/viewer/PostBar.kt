package eu.filipnone.starbooru.ui.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.ui.common.Media
import eu.filipnone.starbooru.ui.common.PostActions
import eu.filipnone.starbooru.ui.common.tintColor
import eu.filipnone.starbooru.ui.frame.glass
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.theme.StarSpringEasing
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * The bar under the picture: the vote, then Download, Share and, apart on
 * the far side, Delete. Labels show beside the icons once there's room.
 */
@Composable
fun PostBar(
    post: Post,
    actions: PostActions,
    onDeleted: () -> Unit,
    onRestored: (Post) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = StarBooruTheme.roles
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labels = maxWidth >= 620.dp
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .glass(RoundedCornerShape(30.dp), roles)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (actions.stars) Stars(post, actions) else Likes(post, actions)
            Spacer(Modifier.width(4.dp))
            BarButton(R.drawable.ic_download, stringResource(R.string.post_download), labels) { actions.download(listOf(post)) }
            BarButton(R.drawable.ic_share, stringResource(R.string.share_button), labels) { actions.share(listOf(post)) }
            Spacer(Modifier.weight(1f))
            if (!actions.guest && !post.deleted) {
                BarButton(R.drawable.ic_delete, stringResource(R.string.post_delete_title), labels, danger = true) {
                    actions.delete(post, onGone = onDeleted, onBack = onRestored)
                }
            }
        }
    }
}

@Composable
private fun Likes(
    post: Post,
    actions: PostActions,
) {
    val roles = StarBooruTheme.roles
    Row(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(roles.surface3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoteButton(R.drawable.ic_thumb_up, stringResource(R.string.post_upvote), post.myVote > 0) { actions.vote(post, 1) }
        Text(
            post.score.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier =
                Modifier
                    .widthIn(min = 24.dp)
                    .semantics { contentDescription = "${post.score}" },
        )
        VoteButton(R.drawable.ic_thumb_down, stringResource(R.string.post_downvote), post.myVote < 0) { actions.vote(post, -1) }
    }
}

@Composable
private fun VoteButton(
    icon: Int,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    val pop by animateFloatAsState(if (on) 1.15f else 1f, tween(420, easing = StarSpringEasing), label = "vote")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .semantics { selected = on }
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            label,
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp).scale(pop),
        )
    }
}

/** Five stars; the ones up to the vote are lit. The average and the count beside them. */
@Composable
private fun Stars(
    post: Post,
    actions: PostActions,
) {
    val mine = post.myStars.toInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (star in 1..Votes.MAX_STARS) {
            val lit = star <= mine
            val label = pluralStringResource(R.plurals.stars_of, star, star)
            Box(
                Modifier
                    .size(40.dp, 44.dp)
                    .clip(CircleShape)
                    .semantics { selected = lit }
                    .clickable(role = Role.Button, onClickLabel = label) { actions.star(post, star) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (lit) R.drawable.ic_star else R.drawable.ic_star_outline),
                    label,
                    tint = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        val average = post.stars
        if (average != null && post.votes > 0u) {
            val comma = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator == ','
            val text = "%.1f".format(Locale.ROOT, average).let { if (comma) it.replace('.', ',') else it }
            Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun BarButton(
    icon: Int,
    label: String,
    showLabel: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) StarBooruTheme.roles.danger else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = if (showLabel) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), if (showLabel) null else label, tint = tint, modifier = Modifier.size(22.dp))
        if (showLabel) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

/** Posts like this one, in a row to run a thumb along. */
@Composable
fun RelatedRow(
    posts: List<Post>,
    onOpen: (Post) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (posts.isEmpty()) return
    val context = LocalContext.current
    androidx.compose.foundation.layout.Column(modifier) {
        Text(
            stringResource(R.string.post_related),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(posts, key = { it.id }) { post ->
                val label = stringResource(R.string.post_alt, post.id.toString())
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(post.tintColor() ?: StarBooruTheme.roles.surface2)
                        .semantics { contentDescription = label }
                        .clickable { onOpen(post) },
                ) {
                    Media.thumb(context, post)?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(112.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The post is in the recycle bin: a notice with Restore. */
@Composable
fun InTrash(
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = StarBooruTheme.roles
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(roles.warnContainer)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.post_in_trash), color = roles.onWarnContainer, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRestore)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.trash_restore), color = roles.onWarnContainer, fontWeight = FontWeight.SemiBold)
        }
    }
}
