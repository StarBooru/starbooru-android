package eu.filipnone.starbooru.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.Rating
import eu.filipnone.starbooru.ui.frame.glass
import eu.filipnone.starbooru.ui.theme.StarBooruTheme

/**
 * The bar while posts are picked: stop, how many, all or none, then what to
 * do with them. It floats above the navigation, where the thumb is.
 */
@Composable
fun BulkBar(
    count: Int,
    full: Boolean,
    canDelete: Boolean,
    onClose: () -> Unit,
    onAll: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val some = count > 0
    BoxWithConstraints(modifier.widthIn(max = 960.dp).fillMaxWidth()) {
        // A phone's width holds the count or the buttons, not both: two rows.
        val narrow = maxWidth < 520.dp
        val head: @Composable RowScope.() -> Unit =
            {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.gallery_stop_selecting))
                }
                Text(
                    pluralStringResource(R.plurals.bulk_selected, count, count),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAll) {
                    Text(stringResource(if (full) R.string.bulk_select_none else R.string.bulk_select_all), maxLines = 1)
                }
            }
        val tools: @Composable RowScope.() -> Unit =
            {
                IconButton(onClick = onEdit, enabled = some) {
                    Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.bulk_edit_title))
                }
                IconButton(onClick = onShare, enabled = some) {
                    Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share_button))
                }
                IconButton(onClick = onDownload, enabled = some) {
                    Icon(painterResource(R.drawable.ic_download), stringResource(R.string.post_download))
                }
                if (canDelete) {
                    IconButton(onClick = onDelete, enabled = some) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            stringResource(R.string.post_delete_title),
                            tint = if (some) StarBooruTheme.roles.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), StarBooruTheme.roles)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            if (narrow) {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) { head() }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) { tools() }
            } else {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    head()
                    tools()
                }
            }
        }
    }
}

/** What a mass edit does: tags to add and to take off, and a rating or none. */
data class BulkChange(val add: List<String>, val remove: List<String>, val rating: Rating?)

/** The words in a tag box, as tags. */
fun tagsIn(text: String): List<String> =
    text
        .split(Regex("\\s+"))
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()

/** The first tag in both lists, which the edit can't both add and take off. */
fun tagInBoth(change: BulkChange): String? = change.add.firstOrNull { it in change.remove }

/** The sheet for editing the picked posts' tags and rating. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkSheet(
    count: Int,
    onApply: (BulkChange) -> Unit,
    onDismiss: () -> Unit,
) {
    var add by rememberSaveable { mutableStateOf("") }
    var remove by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableStateOf<Rating?>(null) }
    val change = BulkChange(tagsIn(add), tagsIn(remove), rating)
    val both = tagInBoth(change)
    val nothing = change.add.isEmpty() && change.remove.isEmpty() && change.rating == null
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(pluralStringResource(R.plurals.bulk_sheet_title, count, count), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = add,
                onValueChange = { add = it },
                label = { Text(stringResource(R.string.bulk_add_tags)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = remove,
                onValueChange = { remove = it },
                label = { Text(stringResource(R.string.bulk_remove_tags)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.rating), style = MaterialTheme.typography.labelLarge)
            val ratings = listOf(null, Rating.SAFE, Rating.SENSITIVE, Rating.QUESTIONABLE, Rating.EXPLICIT)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ratings.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = rating == r,
                        onClick = { rating = r },
                        shape = SegmentedButtonDefaults.itemShape(i, ratings.size),
                        icon = {},
                    ) {
                        Text(ratingLabel(r), maxLines = 1, fontSize = 12.sp)
                    }
                }
            }
            val problem =
                when {
                    both != null -> stringResource(R.string.bulk_tag_both, both)
                    nothing -> stringResource(R.string.bulk_nothing)
                    else -> null
                }
            if (problem != null) {
                Text(problem, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Row {
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(change) }, enabled = problem == null) { Text(stringResource(R.string.bulk_apply)) }
            }
        }
    }
}

@Composable
fun ratingLabel(rating: Rating?): String =
    stringResource(
        when (rating) {
            null -> R.string.bulk_keep_rating
            Rating.SAFE -> R.string.rating_safe
            Rating.SENSITIVE -> R.string.rating_sensitive
            Rating.QUESTIONABLE -> R.string.rating_questionable
            Rating.EXPLICIT -> R.string.rating_explicit
        },
    )
