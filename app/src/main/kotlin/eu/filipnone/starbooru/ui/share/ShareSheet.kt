package eu.filipnone.starbooru.ui.share

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.CoreException
import eu.filipnone.starbooru.core.NewShare
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.Share
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.ui.common.describe
import eu.filipnone.starbooru.ui.theme.StarBooruTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

/**
 * Share: send the files themselves to an app on this device, or make a link
 * anyone can open. A public server's posts have addresses of their own, so
 * there the link is the post's address.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    posts: List<Post>,
    account: Account,
    server: String,
    public: Boolean,
    onFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    var making by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(pluralStringResource(R.plurals.share_title, posts.size, posts.size), style = MaterialTheme.typography.titleLarge)
            AnimatedContent(making, label = "share") { form ->
                when {
                    form && public -> Addresses(posts, server)

                    form -> LinkForm(posts, account, server)

                    else ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice(
                                R.drawable.ic_send,
                                pluralStringResource(R.plurals.share_files_android, posts.size, posts.size),
                                stringResource(R.string.share_files_hint_android),
                            ) {
                                onFiles()
                                onDismiss()
                            }
                            Choice(
                                R.drawable.ic_link,
                                stringResource(R.string.share_link),
                                stringResource(R.string.share_link_hint_android),
                            ) {
                                making = true
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun Choice(
    icon: Int,
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(StarBooruTheme.roles.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A public server: the posts' own addresses, to copy or send. */
@Composable
private fun Addresses(
    posts: List<Post>,
    server: String,
) {
    val text = posts.joinToString("\n") { ShareForm.postAddress(server, it.id) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            pluralStringResource(R.plurals.share_address_lead, posts.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Ready(text)
    }
}

/** A link, once made: its address, Copy and Share via. */
@Composable
private fun Ready(text: String) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StarBooruTheme.roles.surface2)
                    .padding(12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("StarBooru", text)))
                    copied = true
                }
            }) {
                Icon(painterResource(if (copied) R.drawable.ic_check else R.drawable.ic_copy), null, Modifier.size(18.dp))
                Text(stringResource(if (copied) R.string.share_copied else R.string.share_copy), Modifier.padding(start = 8.dp))
            }
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) {
                Icon(painterResource(R.drawable.ic_share), null, Modifier.size(18.dp))
                Text(stringResource(R.string.share_native), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** A private server: a title, how long the link works, then the link. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LinkForm(
    posts: List<Post>,
    account: Account,
    server: String,
) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var title by rememberSaveable { mutableStateOf("") }
    var lifetime by remember { mutableStateOf<Lifetime>(Lifetime.Days(7)) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var made by remember { mutableStateOf<Share?>(null) }
    val done = made
    if (done != null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.share_ready), style = MaterialTheme.typography.bodyMedium)
            Ready(done.url)
        }
        return
    }
    val problem = ShareForm.problem(posts.size, title, lifetime, today)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            pluralStringResource(R.plurals.share_lead, posts.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.share_name)) },
            placeholder = { Text(stringResource(R.string.share_name_placeholder)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.share_lifetime), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareForm.QUICK_DAYS.forEach { d ->
                FilterChip(
                    selected = lifetime == Lifetime.Days(d),
                    onClick = { lifetime = Lifetime.Days(d) },
                    label = { Text(pluralStringResource(R.plurals.share_days, d, d)) },
                )
            }
            val picked = (lifetime as? Lifetime.UntilDay)?.last
            FilterChip(
                selected = lifetime is Lifetime.UntilDay,
                onClick = { picking = true },
                leadingIcon = { Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(18.dp)) },
                label = {
                    Text(picked?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: stringResource(R.string.share_pick_day))
                },
            )
            FilterChip(
                selected = lifetime == Lifetime.UntilRemoved,
                onClick = { lifetime = Lifetime.UntilRemoved },
                label = { Text(stringResource(R.string.share_until_removed)) },
            )
        }
        val words = problem?.let { problemText(it) } ?: error?.let { describe(it, server) }
        if (words != null && (problem !is ShareProblem.DayMissing || lifetime is Lifetime.UntilDay)) {
            Text(words, color = StarBooruTheme.roles.danger, style = MaterialTheme.typography.bodyMedium)
        }
        Row {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Button(
                enabled = problem == null && !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            made =
                                account.call {
                                    it.createShare(
                                        NewShare(
                                            postIds = posts.map { p -> p.id },
                                            title = title.trim().ifEmpty { null },
                                            days = ShareForm.days(lifetime, today)?.toUInt(),
                                        ),
                                    )
                                }
                        } catch (e: CoreException) {
                            error = e
                        }
                        busy = false
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.share_create), Modifier.padding(start = if (busy) 8.dp else 0.dp))
            }
        }
    }
    if (picking) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = today.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                yearRange = today.year..today.plusDays(ShareForm.QUICK_DAYS.last().toLong() + 365).year,
            )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    lifetime =
                        Lifetime.UntilDay(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                    picking = false
                }) { Text(stringResource(R.string.share_done)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state, title = { Text(stringResource(R.string.share_day), Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

@Composable
private fun problemText(p: ShareProblem): String =
    when (p) {
        ShareProblem.NoPosts -> stringResource(R.string.share_error_none)

        is ShareProblem.TooMany -> stringResource(R.string.share_error_many, p.max.toString())

        is ShareProblem.TitleLong -> stringResource(R.string.share_error_title_long, p.max.toString())

        ShareProblem.TitleControl -> stringResource(R.string.share_error_title_control)

        ShareProblem.DayMissing -> stringResource(R.string.share_day_missing)

        ShareProblem.DaySoon -> stringResource(R.string.share_day_soon)

        is ShareProblem.DayLate ->
            stringResource(
                R.string.share_day_late,
                p.last.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())),
                p.max.toString(),
            )
    }
