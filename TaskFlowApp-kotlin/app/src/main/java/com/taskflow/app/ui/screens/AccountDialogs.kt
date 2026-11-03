package com.taskflow.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taskflow.app.data.model.CreateGroupRequest
import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.InviteInfo
import com.taskflow.app.data.remote.ApiErrors
import com.taskflow.app.data.remote.RetrofitClient
import com.taskflow.app.ui.components.QuietAlertDialog
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/*
 * The dialogs that belong to a person and their households rather than to any
 * one screen.
 *
 * This file used to be `DashboardScreen.kt` — the group list, with these
 * dialogs hanging off its overflow menu. The list is gone (DESIGN.md §4.2: it
 * was a transit screen most people passed through with one card on it), but all
 * four dialogs outlived it and are now opened from two places each: the
 * household switcher in the board's app bar, and the Settings tab.
 */

/**
 * How often to look for new invites while the app is on screen. Deliberately
 * slower than the group activity feed: invites are occasional, and each poll
 * is a request for every user sitting in the app.
 *
 * Read by [MainShell], which does the polling.
 */
internal const val INVITE_POLL_MILLIS = 20_000L

/** The wire format for a quiet-hours bound, and what the dialog shows. */
internal val QUIET_HOURS_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// ═══════════════════════════════════════════════════════════════
// Invites
// ═══════════════════════════════════════════════════════════════

/**
 * Pending direct invites, with accept/decline per row.
 *
 * Stays open across responses so a user with several invites can work through
 * them; the caller closes it once the list empties.
 */
@Composable
internal fun PendingInvitesDialog(
    invites: List<InviteInfo>,
    onDismiss: () -> Unit,
    onRespond: (InviteInfo, String) -> Unit,
) {
    QuietAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invitations", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                invites.forEach { invite ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = invite.groupName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            text = "Invited by ${invite.invitedBy}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onRespond(invite, "accept") }) {
                                Text("Accept")
                            }
                            TextButton(onClick = { onRespond(invite, "decline") }) {
                                Text(
                                    text = "Decline",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Redeem a shared invite code.
 *
 * Codes are 12 hex characters from the backend's `generateCode`. Input is
 * lower-cased and trimmed because they get copied out of chat apps, which love
 * to capitalise the first letter.
 */
@Composable
internal fun JoinWithCodeDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }

    QuietAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a household", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Paste the invite code a housemate shared with you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim().lowercase() },
                    label = { Text("Invite code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code) },
                enabled = code.isNotBlank(),
            ) { Text("Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ═══════════════════════════════════════════════════════════════
// Create Group Dialog
// ═══════════════════════════════════════════════════════════════

@Composable
internal fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onGroupCreated: (Group) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    QuietAlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Create a household", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Household name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                )
                AnimatedVisibility(visible = error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Household name is required"
                        return@Button
                    }
                    isLoading = true
                    error = null
                    scope.launch {
                        try {
                            val response = RetrofitClient.getInstance()
                                .groupApi
                                .createGroup(CreateGroupRequest(name.trim(), description.trim()))
                            val created = response.body()
                            if (response.isSuccessful && created != null) {
                                onGroupCreated(created)
                            } else {
                                // Same treatment as the auth screens: show the
                                // backend's message, not the raw JSON body.
                                error = ApiErrors.messageFor(response)
                            }
                        } catch (e: Exception) {
                            error = ApiErrors.forFailure(e)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading,
            ) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Quiet hours (F3).
 *
 * The window when the app will not notify you. A reminder that would land
 * inside it is held until the window opens, not dropped — which is worth saying
 * on the dialog, because "quiet hours" could as easily mean "cancelled".
 *
 * The default wraps midnight, so the two fields are not a simple range and the
 * copy avoids implying they are.
 */
@Composable
internal fun QuietHoursDialog(
    from: String,
    to: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var fromText by remember { mutableStateOf(from) }
    var toText by remember { mutableStateOf(to) }
    var error by remember { mutableStateOf<String?>(null) }

    // "HH:MM", 24-hour. Kept deliberately strict so what the user types is
    // exactly what the server stores.
    fun valid(value: String): Boolean =
        Regex("""^([01]\d|2[0-3]):([0-5]\d)$""").matches(value)

    QuietAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quiet hours", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(visible = error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "Reminders won't arrive between these times. Anything due in that window waits until it's over, so nothing is lost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = fromText,
                        onValueChange = { fromText = it.take(5); error = null },
                        label = { Text("From") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = toText,
                        onValueChange = { toText = it.take(5); error = null },
                        label = { Text("Until") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "24-hour, like 21:00.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!valid(fromText) || !valid(toText)) {
                    error = "Use 24-hour times, like 21:00"
                    return@Button
                }
                if (fromText == toText) {
                    error = "Start and end can't be the same time"
                    return@Button
                }
                onSave(fromText, toText)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
