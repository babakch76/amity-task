package com.taskflow.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskflow.app.ui.components.authAmber
import com.taskflow.app.ui.theme.Appearance
import com.taskflow.app.ui.theme.AppearanceMode

/**
 * **Settings** — the third shell tab, and everything about *you*.
 *
 * It exists because the overflow menu on the group list had quietly become the
 * app's settings screen: quiet hours, appearance, joining a household and
 * signing out were all behind three dots on a screen about households. A menu
 * is a place to hide things; these are things a person goes looking for.
 *
 * Three groups, in the order a person reaches for them:
 *
 *  1. **You** — the name housemates see and the address you sign in with, both
 *     editable here for the first time (`PATCH /me`).
 *  2. **Households** — pending invitations, and joining with a code.
 *  3. **This app** — quiet hours, appearance, and signing out.
 *
 * Appearance is on the device rather than the account, and the row says so:
 * it is a property of this phone, not of you or of your household.
 *
 * There is deliberately no password row. Changing a password needs the old one
 * and a route the backend does not have — the same reason the sign-in screen
 * has no *Forgot?* link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    viewModel: ShellViewModel = viewModel(),
    onBack: () -> Unit = {},
) {
    val me by viewModel.me.collectAsState()
    val invites by viewModel.invites.collectAsState()
    val quietHours by viewModel.quietHours.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()

    var showInvites by remember { mutableStateOf(false) }
    var showJoinByCode by remember { mutableStateOf(false) }
    var showQuietHours by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    var showEmail by remember { mutableStateOf(false) }
    var showSignOut by remember { mutableStateOf(false) }

    val appearance by Appearance.mode.collectAsState()
    val context = LocalContext.current

    // Close the invites dialog once the last one is dealt with.
    if (invites.isEmpty() && showInvites) showInvites = false

    // You: opened from your face on Mine, so it has a way back and no dock.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to your turns")
            }
            if (isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 40.dp),
            ) {
                ProfileHeader(
                    username = me?.username,
                    email = me?.email,
                )

                SettingsGroup("YOU") {
                    SettingsRow(
                        icon = Icons.Default.Badge,
                        label = "Name",
                        value = me?.username,
                        onClick = { showName = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Default.AlternateEmail,
                        label = "Email",
                        value = me?.email,
                        onClick = { showEmail = true },
                    )
                }

                SettingsGroup("HOUSEHOLDS") {
                    SettingsRow(
                        icon = Icons.Default.MailOutline,
                        label = "Invitations",
                        // A count, but of things addressed to you rather than
                        // of anybody's work — the one number the never-list
                        // has no quarrel with.
                        value = if (invites.isEmpty()) "None waiting" else "${invites.size} waiting",
                        onClick = { showInvites = true },
                        enabled = invites.isNotEmpty(),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Default.VpnKey,
                        label = "Join with a code",
                        onClick = { showJoinByCode = true },
                    )
                }

                SettingsGroup("REMINDERS AND LOOK") {
                    SettingsRow(
                        icon = Icons.Default.Notifications,
                        label = "Quiet hours",
                        value = "${quietHours.from.format(QUIET_HOURS_FORMAT)}" +
                            " – ${quietHours.to.format(QUIET_HOURS_FORMAT)}",
                        onClick = { showQuietHours = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Default.DarkMode,
                        label = "Appearance",
                        value = appearance.label,
                        onClick = { showAppearance = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        label = "Sign out",
                        onClick = { showSignOut = true },
                    )
                }

                Spacer(Modifier.height(16.dp))
                // Quiet hours are on the account; appearance is on the phone.
                // Saying which is which here saves the surprise of a reinstall
                // keeping one and losing the other.
                Text(
                    text = "Your name, email and quiet hours follow your account. " +
                        "Appearance is kept on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ─── You ───

    if (showName) {
        EditFieldDialog(
            title = "Change your name",
            label = "Name",
            helper = "This is the name your housemates see.",
            initial = me?.username.orEmpty(),
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            onDismiss = { showName = false },
            onSave = { value, onError ->
                viewModel.updateProfile(username = value) { failure ->
                    if (failure == null) showName = false else onError(failure)
                }
            },
        )
    }

    if (showEmail) {
        EditFieldDialog(
            title = "Change your email",
            label = "Email",
            helper = "You'll sign in with this from now on.",
            initial = me?.email.orEmpty(),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Email),
            onDismiss = { showEmail = false },
            onSave = { value, onError ->
                viewModel.updateProfile(email = value) { failure ->
                    if (failure == null) showEmail = false else onError(failure)
                }
            },
        )
    }

    // ─── Households ───

    if (showInvites && invites.isNotEmpty()) {
        PendingInvitesDialog(
            invites = invites,
            onDismiss = { showInvites = false },
            onRespond = { invite, action ->
                viewModel.respondToInvite(invite.id, action, invite.groupName)
            },
        )
    }

    if (showJoinByCode) {
        JoinWithCodeDialog(
            onDismiss = { showJoinByCode = false },
            onJoin = { code ->
                showJoinByCode = false
                viewModel.redeemCode(code)
            },
        )
    }

    // ─── This app ───

    if (showQuietHours) {
        QuietHoursDialog(
            from = quietHours.from.format(QUIET_HOURS_FORMAT),
            to = quietHours.to.format(QUIET_HOURS_FORMAT),
            onDismiss = { showQuietHours = false },
            onSave = { from, to ->
                showQuietHours = false
                viewModel.setQuietHours(from, to)
            },
        )
    }

    if (showAppearance) {
        AlertDialog(
            onDismissRequest = { showAppearance = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    AppearanceMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Appearance.set(context, mode)
                                    showAppearance = false
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = appearance == mode,
                                onClick = {
                                    Appearance.set(context, mode)
                                    showAppearance = false
                                },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(mode.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppearance = false }) { Text("Done") }
            },
        )
    }

    // Signing out drops the token, and with it anything unsent. Cheap to
    // confirm, and the row is two taps from the board.
    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need your email and password to get back in.") },
            confirmButton = {
                TextButton(onClick = { showSignOut = false; onLogout() }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOut = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Who you are, at the top of your own screen.
 *
 * A monogram rather than a photo: there is no avatar upload, and a grey
 * silhouette would be a promise of one. Never a ring, badge or presence dot —
 * a person is not a status.
 */
@Composable
private fun ProfileHeader(username: String?, email: String?) {
    val context = LocalContext.current
    val myId = remember { (context.applicationContext as? com.taskflow.app.TaskFlowApp)?.tokenManager?.getUserId() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.taskflow.app.ui.components.Avatar(username ?: "·", myId ?: "", 68.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = username ?: "…",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = email.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A labelled card of rows: a small grey heading over one white rounded card. */
@Composable
private fun SettingsGroup(label: String, content: @Composable () -> Unit) {
    com.taskflow.app.ui.components.SectionLabel(
        label.lowercase().replaceFirstChar { it.uppercase() },
        Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
    com.taskflow.app.ui.components.GroupedCard { content() }
}

/**
 * One settings row: icon, label, optional value on the right.
 *
 * [enabled] dims the row rather than hiding it — an Invitations row that
 * disappears when empty is a row you cannot learn exists.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor =
        if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else contentColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}

/**
 * One field, one save — the shape both identity edits take.
 *
 * The dialog stays open on failure, holding what was typed, and shows the
 * server's own wording. A rename refused for a name already taken has to leave
 * the person somewhere they can edit it, not back at a list with a snackbar.
 *
 * Errors use `authAmber()` — the same line the sign-in screen shows for a wrong
 * password, so an error reads the same wherever it appears. Never
 * `colorScheme.error`, which the app reserves for Delete.
 *
 * @param onSave Receives the trimmed value and a callback for the failure
 *   message; the dialog closes only when the caller says it succeeded.
 */
@Composable
private fun EditFieldDialog(
    title: String,
    label: String,
    helper: String,
    initial: String,
    keyboard: KeyboardOptions,
    onDismiss: () -> Unit,
    onSave: (String, (String) -> Unit) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text(label) },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = keyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error ?: helper,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) authAmber()
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank() && value.trim() != initial,
                onClick = { onSave(value.trim()) { error = it } },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
