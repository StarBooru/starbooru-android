package eu.filipnone.starbooru.ui.upload

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.filipnone.starbooru.AppGraph
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.Rating
import eu.filipnone.starbooru.data.upload.Adding
import eu.filipnone.starbooru.data.upload.Batch
import eu.filipnone.starbooru.data.upload.Overall
import eu.filipnone.starbooru.data.upload.UploadNote
import eu.filipnone.starbooru.data.upload.UploadNotifications
import eu.filipnone.starbooru.data.upload.UploadQueue
import eu.filipnone.starbooru.data.upload.UploadStatus
import eu.filipnone.starbooru.data.upload.Words
import eu.filipnone.starbooru.data.upload.transferLabel
import eu.filipnone.starbooru.ui.common.LocalNotices
import eu.filipnone.starbooru.ui.common.LocalReducedMotion
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.frame.LocalGlassSource
import eu.filipnone.starbooru.ui.frame.WidthClass
import eu.filipnone.starbooru.ui.frame.glass
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import eu.filipnone.starbooru.ui.theme.StarEasing

/**
 * Upload: files and links wait here as tiles for their tags, then go up in
 * the background, two at a time, and keep going after the app closes.
 */
@Composable
fun UploadScreen(
    graph: AppGraph,
    onOpenPost: (Long) -> Unit,
) {
    val context = LocalContext.current
    val vm = viewModel { UploadViewModel(UploadQueue.get(context.applicationContext), graph.account) }
    UploadPage(vm, onOpenPost)
}

/** The page over its model, which tests make over a queue of their own. */
@Composable
internal fun UploadPage(
    vm: UploadViewModel,
    onOpenPost: (Long) -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val adding by vm.adding.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()
    val res = LocalResources.current
    val notices = LocalNotices.current
    val frame = LocalFrame.current
    val reduced = LocalReducedMotion.current
    val focus = LocalFocusManager.current
    val links = info?.urlImport ?: true
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    var linking by rememberSaveable { mutableStateOf(false) }
    var askedToNotify by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { UploadNotifications.seen(context) }
    LaunchedEffect(vm) {
        vm.notes.collect { note ->
            val text =
                when (note) {
                    is UploadNote.Skipped -> Words.skipped(res, note.names)
                    UploadNote.NothingToPaste -> res.getString(R.string.upload_paste_nothing)
                    is UploadNote.Finished -> Words.summary(res, note.tally)
                }
            if (text != null) notices?.say(text)
        }
    }

    val photos =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris -> vm.takeUris(uris) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.takeUris(uris) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::takeTree) }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val pickPhotos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    val paste = {
        val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
        vm.paste(runCatching { clipboard?.primaryClip }.getOrNull())
    }
    val send = {
        focus.clearFocus()
        if (vm.start()) {
            // Asked once, with the first upload: the notification says how it goes with the app closed.
            val needs =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needs && !askedToNotify) {
                askedToNotify = true
                notify.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val actions =
        TileActions(
            open = { item ->
                when (val s = item.status) {
                    UploadStatus.Draft -> opened = item.id

                    is UploadStatus.Done -> onOpenPost(s.postId)

                    is UploadStatus.Duplicate -> s.postId?.let(onOpenPost)

                    is UploadStatus.Failed ->
                        if (item.retryable) {
                            notices?.offer(s.message, res.getString(R.string.retry)) { vm.retry(item.id) }
                        } else {
                            notices?.say(s.message)
                        }

                    else -> {}
                }
            },
            remove = { vm.remove(it.id) },
            cancel = { vm.cancel(it.id) },
            retry = { vm.retry(it.id) },
        )

    val overall = remember(state) { state.overall() }
    val drafts = state.drafts
    val glass = rememberHazeState()
    val density = LocalDensity.current
    var barHeight by remember { mutableIntStateOf(0) }
    val dir = LocalLayoutDirection.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = if (frame.width == WidthClass.Compact) 8.dp else 16.dp
        val tile = minOf(168.dp, maxWidth * 0.3f)
        val barShown = drafts > 0 || overall != null
        val barSpace = if (barShown) with(density) { barHeight.toDp() } else 0.dp
        val padding =
            PaddingValues(
                start = frame.padding.calculateStartPadding(dir) + side,
                end = frame.padding.calculateEndPadding(dir) + side,
                top = frame.padding.calculateTopPadding(),
                bottom = frame.padding.calculateBottomPadding() + barSpace + 16.dp,
            )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(tile),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().hazeSource(glass),
        ) {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
                Header(
                    finished = state.items.any { it.finished },
                    clearable = state.items.any { it.status !is UploadStatus.Sending },
                    onClearFinished = vm::clearFinished,
                    onClear = vm::clear,
                )
            }
            item(key = "sources", span = { GridItemSpan(maxLineSpan) }) {
                Sources(
                    links = links,
                    linking = linking,
                    folderTags = state.folderTags,
                    onPhotos = pickPhotos,
                    onFiles = { files.launch(arrayOf("image/*", "video/*")) },
                    onFolder = { folder.launch(null) },
                    onLink = { linking = !linking },
                    onPaste = paste,
                    onFolderTags = vm::setFolderTags,
                )
            }
            if (linking && links) {
                item(key = "links", span = { GridItemSpan(maxLineSpan) }) {
                    LinkCard(onAdd = vm::addLinks, onClose = { linking = false })
                }
            }
            adding?.let { a ->
                item(key = "adding", span = { GridItemSpan(maxLineSpan) }) { AddingRow(a) }
            }
            if (state.items.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { Empty(onPhotos = pickPhotos) }
            } else {
                item(key = "add") { AddTile(onClick = pickPhotos, modifier = if (reduced) Modifier else Modifier.animateItem()) }
                items(state.items, key = { it.id }) { item ->
                    UploadTile(
                        item = item,
                        file = vm.fileOf(item.id),
                        actions = actions,
                        modifier = if (reduced) Modifier else Modifier.animateItem(),
                    )
                }
            }
        }

        val ime = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val bottom = maxOf(frame.padding.calculateBottomPadding(), ime + 8.dp)
        CompositionLocalProvider(LocalGlassSource provides glass) {
            AnimatedVisibility(
                visible = barShown,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250, easing = StarEasing)) { it / 2 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = frame.padding.calculateStartPadding(dir) + side,
                            end = frame.padding.calculateEndPadding(dir) + side,
                            bottom = bottom,
                        ).widthIn(max = 920.dp)
                        .fillMaxWidth()
                        .onSizeChanged { barHeight = it.height },
            ) {
                UploadBar(
                    batch = state.batch,
                    drafts = drafts,
                    overall = overall,
                    problem = problem?.let { Words.badTag(res, it) },
                    compact = frame.width == WidthClass.Compact,
                    onTags = vm::setSharedTags,
                    onSource = vm::setSource,
                    onRating = vm::setSharedRating,
                    onSend = send,
                )
            }
        }
    }

    val open = opened?.let { id -> state.get(id) }
    if (open != null && open.status == UploadStatus.Draft) {
        ItemSheet(
            item = open,
            file = vm.fileOf(open.id),
            shared = state.batch.rating,
            onTags = { vm.setTags(open.id, it) },
            onRating = { vm.setRating(open.id, it) },
            onRemove = {
                vm.remove(open.id)
                opened = null
            },
            onClose = { opened = null },
        )
    } else if (opened != null) {
        LaunchedEffect(opened) { opened = null }
    }
}

@Composable
private fun Header(
    finished: Boolean,
    clearable: Boolean,
    onClearFinished: () -> Unit,
    onClear: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.nav_upload),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = StarBooruTheme.roles.onSurface,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        if (clearable) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.upload_clear), tint = StarBooruTheme.roles.danger)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (finished) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.upload_clear_finished)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_check), null) },
                            onClick = {
                                menu = false
                                onClearFinished()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.upload_clear)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) },
                        onClick = {
                            menu = false
                            onClear()
                        },
                    )
                }
            }
        }
    }
}

/** Where files come from, each a button with its name, and the folder-name switch beside the folder. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Sources(
    links: Boolean,
    linking: Boolean,
    folderTags: Boolean,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onFolder: () -> Unit,
    onLink: () -> Unit,
    onPaste: () -> Unit,
    onFolderTags: (Boolean) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceButton(R.drawable.ic_gallery, stringResource(R.string.upload_photos), onPhotos)
        SourceButton(R.drawable.ic_add, stringResource(R.string.upload_files), onFiles)
        SourceButton(R.drawable.ic_folder, stringResource(R.string.upload_folder), onFolder)
        if (links) SourceButton(R.drawable.ic_link, stringResource(R.string.upload_link), onLink, on = linking)
        SourceButton(R.drawable.ic_copy, stringResource(R.string.upload_paste), onPaste)
        FilterChip(
            selected = folderTags,
            onClick = { onFolderTags(!folderTags) },
            label = { Text(stringResource(R.string.upload_folder_tags)) },
            leadingIcon = {
                Icon(painterResource(if (folderTags) R.drawable.ic_check else R.drawable.ic_label), null, Modifier.size(18.dp))
            },
            modifier = Modifier.heightIn(min = 40.dp),
        )
    }
}

@Composable
private fun SourceButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    on: Boolean = false,
) {
    val roles = StarBooruTheme.roles
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
        colors =
            androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = if (on) roles.primaryContainer else roles.surface2,
                contentColor = if (on) roles.onPrimaryContainer else roles.onSurface,
            ),
    ) {
        Icon(painterResource(icon), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1)
    }
}

/** The links box: typed or pasted addresses, one per line. */
@Composable
private fun LinkCard(
    onAdd: (String) -> Boolean,
    onClose: () -> Unit,
) {
    val roles = StarBooruTheme.roles
    var text by rememberSaveable { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val add = {
        if (onAdd(text)) {
            text = ""
            wrong = false
        } else {
            wrong = true
        }
    }
    val label = stringResource(R.string.upload_urls_label)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(roles.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                wrong = false
            },
            placeholder = { Text(stringResource(R.string.upload_urls_placeholder)) },
            isError = wrong,
            supportingText = if (wrong) ({ Text(stringResource(R.string.upload_urls_only)) }) else null,
            minLines = 2,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = add, enabled = text.isNotBlank()) {
                Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add))
            }
        }
    }
}

@Composable
private fun AddingRow(adding: Adding) {
    val shown by animateFloatAsState(adding.done.toFloat() / adding.total.coerceAtLeast(1), tween(200), label = "adding")
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.upload_adding, adding.done + 1, adding.total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
    }
}

/** Nothing on the list: the photo picker, as big as the page, and what else works. */
@Composable
private fun Empty(onPhotos: () -> Unit) {
    val roles = StarBooruTheme.roles
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .clip(shape)
            .border(2.dp, roles.outline.copy(alpha = 0.5f), shape)
            .background(roles.surface)
            .clickable(role = Role.Button, onClick = onPhotos)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(R.drawable.ic_upload), null, tint = roles.primary, modifier = Modifier.size(48.dp))
        Text(
            stringResource(R.string.upload_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = roles.onSurface2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Button(onClick = onPhotos) {
            Icon(painterResource(R.drawable.ic_gallery), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.upload_photos))
        }
    }
}

/**
 * The floating bar: how far what's on its way has got, and the tags, source
 * and rating everything waiting goes with, and Upload.
 */
@Composable
private fun UploadBar(
    batch: Batch,
    drafts: Int,
    overall: Overall?,
    problem: String?,
    compact: Boolean,
    onTags: (String) -> Unit,
    onSource: (String) -> Unit,
    onRating: (Rating) -> Unit,
    onSend: () -> Unit,
) {
    val roles = StarBooruTheme.roles
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(28.dp), roles)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(visible = overall != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            overall?.let { OverallRow(it) }
        }
        if (drafts > 0) {
            val tags: @Composable (Modifier) -> Unit = { m ->
                Capsule(
                    text = batch.tags,
                    onText = onTags,
                    placeholder = stringResource(R.string.upload_shared_tags),
                    invalid = problem != null,
                    ime = ImeAction.Send,
                    onIme = onSend,
                    modifier = m,
                )
            }
            val source: @Composable (Modifier) -> Unit = { m ->
                Capsule(
                    text = batch.source,
                    onText = onSource,
                    placeholder = stringResource(R.string.upload_source_placeholder),
                    label = stringResource(R.string.upload_source),
                    keyboard = KeyboardType.Uri,
                    modifier = m,
                )
            }
            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags(Modifier.weight(1f))
                    SendButton(drafts, onSend)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RatingButton(batch.rating, onRating)
                    source(Modifier.weight(1f))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags(Modifier.weight(3f))
                    source(Modifier.weight(2f))
                    RatingButton(batch.rating, onRating)
                    SendButton(drafts, onSend)
                }
            }
            if (problem != null) {
                Text(
                    problem,
                    color = roles.danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun OverallRow(overall: Overall) {
    val res = LocalResources.current
    val shown by animateFloatAsState((overall.percent ?: 0) / 100f, tween(300), label = "overall")
    val words = pluralStringResource(R.plurals.upload_progress, overall.finished, overall.finished, overall.files)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                words,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = StarBooruTheme.roles.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                transferLabel(overall.sent, overall.total, overall.speed) { Words.size(res, it) },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (overall.percent == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SendButton(
    drafts: Int,
    onSend: () -> Unit,
) {
    Button(onClick = onSend, modifier = Modifier.heightIn(min = FieldHeight), contentPadding = PaddingValues(start = 14.dp, end = 18.dp)) {
        Icon(painterResource(R.drawable.ic_upload), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(pluralStringResource(R.plurals.upload_start, drafts, drafts), maxLines = 1)
    }
}

@Composable
private fun RatingButton(
    rating: Rating,
    onRating: (Rating) -> Unit,
) {
    val roles = StarBooruTheme.roles
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.rating)
    val name = ratingName(rating)
    Box {
        Row(
            Modifier
                .heightIn(min = FieldHeight)
                .clip(CircleShape)
                .background(roles.surface)
                .border(1.dp, roles.outline, CircleShape)
                .clickable(role = Role.DropdownList, onClickLabel = label) { open = true }
                .semantics { contentDescription = "$label: $name" }
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = roles.onSurface, maxLines = 1)
            Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.padding(start = 4.dp).size(18.dp), tint = roles.onSurface2)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Rating.entries.forEach { r ->
                DropdownMenuItem(
                    text = { Text(ratingName(r)) },
                    trailingIcon = if (r == rating) ({ Icon(painterResource(R.drawable.ic_check), null) }) else null,
                    onClick = {
                        open = false
                        onRating(r)
                    },
                )
            }
        }
    }
}

/** A one-line field as round as the button beside it, as the web's bar has them. */
@Composable
private fun Capsule(
    text: String,
    onText: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String = placeholder,
    invalid: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    ime: ImeAction = ImeAction.Done,
    onIme: () -> Unit = {},
) {
    val roles = StarBooruTheme.roles
    val edge = if (invalid) roles.danger else roles.outline
    BasicTextField(
        value = text,
        onValueChange = onText,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = roles.onSurface),
        cursorBrush = SolidColor(roles.primary),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = keyboard,
                imeAction = ime,
            ),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        modifier = modifier.semantics { contentDescription = label },
        decorationBox = { field ->
            Box(
                Modifier
                    .heightIn(min = FieldHeight)
                    .clip(CircleShape)
                    .background(roles.surface)
                    .border(if (invalid) 2.dp else 1.dp, edge, CircleShape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = roles.onSurface2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                field()
            }
        },
    )
}

private val FieldHeight: Dp = 48.dp
