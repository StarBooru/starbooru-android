package eu.filipnone.starbooru.ui.signin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.core.normalizeServer
import eu.filipnone.starbooru.ui.common.describe
import eu.filipnone.starbooru.ui.common.openInTab
import eu.filipnone.starbooru.ui.frame.LocalGlassSource
import eu.filipnone.starbooru.ui.frame.glass
import eu.filipnone.starbooru.ui.theme.StarBooruTheme

/**
 * The sign-in card, over the aurora as on the web. The server's name and
 * version show as soon as the address answers, so the user knows it's the
 * right one before typing a password.
 */
@Composable
fun SignInScreen(vm: SignInViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val url by vm.openUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val roles = StarBooruTheme.roles
    LaunchedEffect(url) {
        url?.let {
            openInTab(context, it)
            vm.urlOpened()
        }
    }
    androidx.activity.compose.BackHandler(enabled = state.viaProvider) { vm.cancelProvider() }

    val glassSource = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        // The aurora behind the card, which the card's glass blurs.
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .background(Brush.radialGradient(listOf(roles.aurora, MaterialTheme.colorScheme.background)))
                .hazeSource(glassSource),
        )
        CompositionLocalProvider(LocalGlassSource provides glassSource) { SignInCard(state, vm) }
    }
}

@Composable
private fun SignInCard(
    state: SignInState,
    vm: SignInViewModel,
) {
    val roles = StarBooruTheme.roles
    run {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(28.dp), roles)
                    .padding(24.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Brand(state)
                if (state.firstAccount) Banner(stringResource(R.string.login_first_user))
                ServerField(state, vm)
                OutlinedTextField(
                    value = state.username,
                    onValueChange = vm::setUsername,
                    label = { Text(stringResource(R.string.login_username)) },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(state, vm)
                state.error?.let { e ->
                    Banner(describe(e, state.known?.server ?: state.server, signingIn = true), error = true)
                }
                Button(
                    onClick = vm::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            when {
                                state.busy -> R.string.login_wait
                                state.firstAccount -> R.string.login_create_admin
                                state.makingAccount -> R.string.login_create
                                else -> R.string.login_sign_in
                            },
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val provider = state.known?.info?.oidc
                if (provider != null && !state.makingAccount) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.login_or),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        HorizontalDivider(Modifier.weight(1f))
                    }
                    FilledTonalButton(
                        onClick = vm::signInWithProvider,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        if (state.viaProvider) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.oidc_finishing))
                        } else {
                            Text(stringResource(R.string.login_with, provider))
                        }
                    }
                }
                if (state.canRegister && !state.firstAccount) {
                    TextButton(onClick = { vm.setRegistering(!state.registering) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (state.registering) R.string.login_have_account else R.string.login_want_account))
                    }
                }
                if (state.known?.info?.guests == true) {
                    TextButton(onClick = vm::browseAsGuest, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.guest_keep_browsing))
                    }
                }
            }
        }
    }
}

@Composable
private fun Brand(state: SignInState) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(72.dp).aspectRatio(512f / 434f),
        )
        Spacer(Modifier.height(12.dp))
        Icon(
            painterResource(R.drawable.ic_brand_wordmark),
            contentDescription = stringResource(R.string.app_name),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(26.dp).aspectRatio(648f / 103f),
        )
        Spacer(Modifier.height(8.dp))
        val info = state.known?.info
        Text(
            if (info !=
                null
            ) {
                stringResource(R.string.login_server_version, info.name, info.version)
            } else {
                stringResource(R.string.login_tagline)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ServerField(
    state: SignInState,
    vm: SignInViewModel,
) {
    val check = state.check
    OutlinedTextField(
        value = state.server,
        onValueChange = vm::setServer,
        label = { Text(stringResource(R.string.login_server)) },
        placeholder = { Text(stringResource(R.string.login_server_placeholder)) },
        singleLine = true,
        enabled = !state.busy,
        isError = check is ServerCheck.Failed,
        supportingText =
            (check as? ServerCheck.Failed)?.let { failed ->
                { Text(describe(failed.error, failed.server)) }
            },
        trailingIcon = {
            when (check) {
                ServerCheck.Checking -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)

                is ServerCheck.Known -> Icon(painterResource(R.drawable.ic_check), null, tint = StarBooruTheme.roles.ok)

                is ServerCheck.Failed ->
                    IconButton(onClick = vm::retryCheck) { Icon(painterResource(R.drawable.ic_restore), stringResource(R.string.retry)) }

                ServerCheck.Empty -> {}
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    // What the address will be kept as, once it differs from what's typed.
    val kept = normalizeServer(state.server)
    AnimatedVisibility(kept.isNotEmpty() && kept != state.server.trim() && check !is ServerCheck.Failed) {
        Text(
            kept,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun PasswordField(
    state: SignInState,
    vm: SignInViewModel,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = state.password,
        onValueChange = vm::setPassword,
        label = { Text(stringResource(R.string.login_password)) },
        singleLine = true,
        enabled = !state.busy,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Icon(painterResource(if (shown) R.drawable.ic_eye_off else R.drawable.ic_eye), null)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { vm.submit() }),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Banner(
    text: String,
    error: Boolean = false,
) {
    val roles = StarBooruTheme.roles
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) roles.onDangerContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (error) roles.dangerContainer else MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
