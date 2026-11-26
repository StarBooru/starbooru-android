package eu.filipnone.starbooru.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.shownServer
import eu.filipnone.starbooru.ui.frame.LocalFrame
import eu.filipnone.starbooru.ui.theme.StarBooruTheme

/** A part of a legal page: its heading, then paragraphs or a list. */
private data class Section(val heading: Int, val paragraphs: List<Int> = emptyList(), val points: List<Int> = emptyList())

private val PRIVACY =
    listOf(
        Section(R.string.privacy_responsible, paragraphs = listOf(R.string.privacy_responsible_body)),
        Section(
            R.string.privacy_server,
            points =
                listOf(
                    R.string.privacy_server_account,
                    R.string.privacy_server_profile,
                    R.string.privacy_server_sessions,
                    R.string.privacy_server_files,
                    R.string.privacy_server_content,
                    R.string.privacy_server_forum,
                    R.string.privacy_server_shares,
                    R.string.privacy_server_sites,
                    R.string.privacy_server_limits,
                    R.string.privacy_server_guest_limits,
                ),
        ),
        // What stays on the device is this app's own, not the web's.
        Section(
            R.string.privacy_device,
            points =
                listOf(
                    R.string.privacy_device_storage_android,
                    R.string.privacy_device_sign_in_android,
                    R.string.privacy_device_sign_out_android,
                    R.string.privacy_device_cache_android,
                ),
        ),
        Section(
            R.string.privacy_not_done,
            points =
                listOf(
                    R.string.privacy_not_done_tracking,
                    R.string.privacy_not_done_contacts,
                    R.string.privacy_not_done_boorus,
                    R.string.privacy_not_done_reverse,
                    R.string.privacy_not_done_ai,
                    R.string.privacy_not_done_library,
                    R.string.privacy_not_done_logs,
                ),
        ),
        Section(
            R.string.privacy_who_sees,
            paragraphs = listOf(R.string.privacy_who_sees_body, R.string.privacy_who_sees_public, R.string.privacy_who_sees_profiles),
        ),
        Section(
            R.string.privacy_keeping,
            points =
                listOf(
                    R.string.privacy_keeping_bin,
                    R.string.privacy_keeping_sessions,
                    R.string.privacy_keeping_admins,
                    R.string.privacy_keeping_backups,
                ),
        ),
    )

private val TERMS =
    listOf(
        Section(R.string.terms_account, points = listOf(R.string.terms_account_keys, R.string.terms_account_admins)),
        Section(
            R.string.terms_content,
            points = listOf(R.string.terms_content_rights, R.string.terms_content_yours, R.string.terms_content_originals),
        ),
        Section(R.string.terms_fair, points = listOf(R.string.terms_fair_access, R.string.terms_fair_load)),
        Section(R.string.terms_warranty, paragraphs = listOf(R.string.terms_warranty_body)),
    )

/** The privacy policy or the terms, for the server in use, with a way to the other. */
@Composable
fun LegalScreen(
    privacy: Boolean,
    server: String,
    serverName: String?,
    onOther: () -> Unit,
) {
    val sections = if (privacy) PRIVACY else TERMS
    val shown = shownServer(server)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(LocalFrame.current.padding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(if (privacy) R.string.privacy_title else R.string.terms_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            if (server.isNotEmpty()) {
                Text(
                    if (serverName != null) {
                        stringResource(R.string.legal_server, serverName, shown)
                    } else {
                        stringResource(R.string.legal_server_address, shown)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(stringResource(if (privacy) R.string.privacy_lead else R.string.terms_lead), style = MaterialTheme.typography.bodyLarge)
            sections.forEach { section ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(StarBooruTheme.roles.surface)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(section.heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    section.paragraphs.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                    section.points.forEach { point ->
                        Row {
                            Text("•", modifier = Modifier.padding(end = 10.dp), color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(point), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            TextButton(onClick = onOther) {
                Text(stringResource(if (privacy) R.string.legal_terms_short else R.string.legal_privacy_short))
            }
        }
    }
}
