package eu.filipnone.starbooru.ui.upload

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.Rating
import eu.filipnone.starbooru.core.checkTags
import eu.filipnone.starbooru.data.upload.Accept
import eu.filipnone.starbooru.data.upload.UploadItem
import eu.filipnone.starbooru.data.upload.UploadSource
import eu.filipnone.starbooru.data.upload.UploadStatus
import eu.filipnone.starbooru.data.upload.Words
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A draft opened from its tile: a bigger picture, what is known of it, and
 * its own rating and tags. It closes by itself once the item is sent or gone.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ItemSheet(
    item: UploadItem,
    file: File,
    shared: Rating,
    onTags: (String) -> Unit,
    onRating: (Rating?) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val roles = StarBooruTheme.roles
    val res = LocalResources.current
    val context = LocalContext.current
    val hide: (() -> Unit) -> Unit = { then -> scope.launch { sheet.hide() }.invokeOnCompletion { then() } }
    var tags by rememberSaveable(item.id) { mutableStateOf(item.tags) }
    val problem = remember(tags) { checkTags(tags).bad?.let { Words.badTag(res, it) } }
    LaunchedEffect(item.status) { if (item.status != UploadStatus.Draft) hide(onClose) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = roles.surface,
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = roles.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { hide(onClose) }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.upload_close))
                }
            }
            if (item.isFile) {
                val request = remember(item.id) { localPicture(context, item, file, PREVIEW_PX) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(TileShape)
                        .background(roles.surface2),
                ) {
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    )
                }
            }
            Facts(item, file)
            Label(stringResource(R.string.rating))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = item.rating == null,
                    onClick = { onRating(null) },
                    label = { Text(stringResource(R.string.upload_rating_usual, ratingName(shared))) },
                )
                Rating.entries.forEach { r ->
                    FilterChip(selected = item.rating == r, onClick = { onRating(r) }, label = { Text(ratingName(r)) })
                }
            }
            Label(stringResource(R.string.post_tags))
            val label = stringResource(R.string.upload_file_tags_label, item.name)
            OutlinedTextField(
                value = tags,
                onValueChange = {
                    tags = it
                    onTags(it)
                },
                placeholder = { Text(stringResource(R.string.upload_file_tags_placeholder)) },
                isError = problem != null,
                supportingText = problem?.let { { Text(it) } },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val link = item.source as? UploadSource.Link
                if (link != null) {
                    val uri = LocalUriHandler.current
                    TextButton(onClick = { runCatching { uri.openUri(link.url) } }) {
                        Icon(painterResource(R.drawable.ic_link), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.upload_open_link))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { hide { onRemove() } }) {
                    Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(18.dp), tint = roles.danger)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.upload_remove_item), color = roles.danger)
                }
            }
        }
    }
}

/** Where it comes from, its size, its sides and its type; each left out when unknown. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(
    item: UploadItem,
    file: File,
) {
    val source = item.source
    val sides by produceState<Pair<Int, Int>?>(null, item.id) {
        if (source is UploadSource.Local && !Accept.isVideo(source.name, source.mime)) {
            value =
                withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    runCatching { BitmapFactory.decodeFile(file.path, bounds) }
                    if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
                }
        }
    }
    val facts =
        buildList {
            when (source) {
                is UploadSource.Local -> {
                    source.path.trimStart('/').takeIf { it.isNotEmpty() }?.let { add(R.string.post_source to it) }
                    sizeOf(item)?.let { add(R.string.post_size to it) }
                    sides?.let { (w, h) -> add(R.string.post_dimensions to "$w × $h") }
                    (Accept.formatOf(source.mime) ?: Accept.formatByName(source.name))?.let { add(R.string.post_type to it) }
                }

                is UploadSource.Link -> {
                    Accept.hostOf(source.url).takeIf { it.isNotEmpty() }?.let { add(R.string.post_source to it) }
                    Accept.formatByName(source.url)?.let { add(R.string.post_type to it) }
                }
            }
        }
    if (facts.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        facts.forEach { (label, value) ->
            Column {
                Label(stringResource(label))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StarBooruTheme.roles.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ratingName(rating: Rating): String =
    stringResource(
        when (rating) {
            Rating.SAFE -> R.string.rating_safe
            Rating.SENSITIVE -> R.string.rating_sensitive
            Rating.QUESTIONABLE -> R.string.rating_questionable
            Rating.EXPLICIT -> R.string.rating_explicit
        },
    )

/** The size the sheet's picture is decoded at, in pixels. */
private const val PREVIEW_PX = 1080
