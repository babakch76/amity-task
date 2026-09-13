package com.taskflow.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.taskflow.app.TaskFlowApp
import com.taskflow.app.data.model.CreateGroupRequest
import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.InviteInfo
import com.taskflow.app.data.remote.ApiErrors
import com.taskflow.app.data.remote.RetrofitClient
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * How often to look for new invites while the app is on screen. Deliberately
 * slower than the group activity feed: invites are occasional, and each poll
 * is a request for every user sitting in the app.
 *
 * Read by [MainShell], which does the polling now that invites are answered in
 * the Settings tab rather than from this screen's app bar.
 */
internal const val INVITE_POLL_MILLIS = 20_000L

/**
 * **My groups** — the first of the three shell tabs.
 *
 * One question only: which households am I in, and which one am I opening? It
 * used to carry the whole of the app's personal settings in an overflow menu,
 * because there was nowhere else for them to go. There is now: everything
 * about *you* moved to the Settings tab, and this screen kept only what is
 * about the households themselves — the list, creating one, and the first-run
 * starters a new one is offered.
 *
 * The bottom bar and the snackbar belong to [MainShell]; so does the
 * [DashboardViewModel], which is shared with the other two tabs.
 *
 * @param onGroupClick Opens that household's board, outside the shell.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onGroupClick: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    // Joining lives in Settings → Households, but the empty state has to offer
    // it too: somebody with no households is the one person for whom bouncing
    // off to another tab to find the other half of the answer is worst.
    var showJoinByCode by remember { mutableStateOf(false) }
    // The group a first-run starter list is being offered for, if any.
    var starterFor by remember { mutableStateOf<Group?>(null) }
    var starterError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val myUserId = remember {
        (context.applicationContext as? TaskFlowApp)?.tokenManager?.getUserId()
    }

    // In exactly one household, the group list is a menu with one item — skip
    // it and land on the board. Fires once; Back then leaves you here.
    val autoOpenGroupId by viewModel.autoOpenGroupId.collectAsState()
    LaunchedEffect(autoOpenGroupId) {
        autoOpenGroupId?.let { id ->
            viewModel.consumeAutoOpen()
            onGroupClick(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My groups",
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Group") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            // Pull-to-refresh: the toolbar icon stays for discoverability, but
            // the swipe is what people actually reach for on a list.
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val state = uiState) {
                    is DashboardUiState.Loading -> LoadingState()
                    is DashboardUiState.Error -> ErrorState(
                        message = state.message,
                        onRetry = { viewModel.refresh() },
                    )
                    is DashboardUiState.Success -> {
                        if (state.groups.isEmpty()) {
                            EmptyState(
                                onCreateGroup = { showCreateDialog = true },
                                onJoinWithCode = { showJoinByCode = true },
                            )
                        } else {
                            GroupList(
                                groups = state.groups,
                                onGroupClick = onGroupClick,
                            )
                        }
                    }
                }
            }
        }
    }

    // ─── Join with a shared code ───
    if (showJoinByCode) {
        JoinWithCodeDialog(
            onDismiss = { showJoinByCode = false },
            onJoin = { code ->
                showJoinByCode = false
                viewModel.redeemCode(code)
            },
        )
    }

    // ─── Create Group Dialog ───
    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onGroupCreated = { group ->
                showCreateDialog = false
                viewModel.refresh()
                // Straight into the offer. A new household is at its most
                // willing to agree things in the minute after it is created,
                // and an empty board is the worst thing to hand it.
                starterFor = group
            },
        )
    }

    starterFor?.let { group ->
        FirstRunStarters(
            groupName = group.name,
            working = isWorking,
            error = starterError,
            onSkip = {
                starterFor = null
                starterError = null
                onGroupClick(group.id)
            },
            onAdd = { chosen ->
                starterError = null
                viewModel.addStarterChores(group.id, myUserId, chosen) { failure ->
                    if (failure == null) {
                        starterFor = null
                        onGroupClick(group.id)
                    } else {
                        starterError = failure
                    }
                }
            },
        )
    }
}

/** The wire format for a quiet-hours bound, and what the dialog shows. */
internal val QUIET_HOURS_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// ═══════════════════════════════════════════════════════════════
// Sub-components
// ═══════════════════════════════════════════════════════════════

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading your groups…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onRetry) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun EmptyState(onCreateGroup: () -> Unit, onJoinWithCode: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.FolderOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No groups yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create a group or ask a teammate for an invite.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(onClick = onCreateGroup) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create your first group")
            }
            Spacer(modifier = Modifier.height(8.dp))
            // The other half of "ask a teammate for an invite" — without this,
            // someone handed a code has nowhere to type it.
            TextButton(onClick = onJoinWithCode) {
                Icon(
                    Icons.Default.VpnKey,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Join with a code")
            }
        }
    }
}

@Composable
private fun GroupList(
    groups: List<Group>,
    onGroupClick: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            // Clears the FAB so the last row is reachable.
            bottom = 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(groups, key = { it.id }) { group ->
            GroupCard(
                group = group,
                onClick = { onGroupClick(group.id) },
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    onClick: () -> Unit,
) {
    // Deterministic color from group ID for the avatar
    val avatarColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
    )
    val avatarColor = avatarColors[group.id.hashCode().and(0x7FFFFFFF) % avatarColors.size]

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ─── Group Avatar ───
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = .15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    tint = avatarColor,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // ─── Text ───
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (group.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatCreatedAt(group.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                )
            }

            // ─── Chevron ───
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f),
            )
        }
    }
}

/**
 * Format an OffsetDateTime to a human-friendly string.
 */
private fun formatCreatedAt(date: OffsetDateTime?): String {
    if (date == null) return ""
    return try {
        date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (_: Exception) {
        ""
    }
}

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invitations", fontWeight = FontWeight.Bold) },
        text = {
            // Scrollable and height-capped. Without this, AlertDialog clips its
            // content and everything past roughly the sixth invite becomes
            // unreachable — no scroll, no indication anything is missing.
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a group", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Paste the invite code a teammate shared with you.",
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
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onGroupCreated: (Group) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Create Group", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Group name") },
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
                        error = "Group name is required"
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
                            error = "Network error: ${e.localizedMessage}"
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

    AlertDialog(
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
