package com.taskflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.taskflow.app.TaskFlowApp
import com.taskflow.app.data.model.Group

/**
 * The first tab: **a household's board**, not a list of households.
 *
 * The list is gone as a destination, which is DESIGN.md §4.2 — it was a transit
 * screen most people passed through with one card on it, and the app now opens
 * straight onto the board of the household you last had open. Changing
 * household is the switcher in that board's app bar
 * ([com.taskflow.app.ui.components.HouseholdSwitcher]), which is also the only
 * remaining home for *Create a household* and *Join with a code*.
 *
 * This composable is the thin part around that: it decides which household is
 * open, handles the case where there are none, and carries the create flow and
 * its first-run starters. The board itself is still [GroupDetailScreen].
 *
 * ### Which household opens
 *
 * [currentGroupId] is held by [MainShell], not here, because **My chores**
 * needs to set it too — tapping one of your turns there switches this tab to
 * that household rather than pushing a second copy of the board onto a stack.
 * This composable only resolves it: a stored id that names a household you have
 * since left, or one belonging to whoever was signed in before, falls back to
 * the first in the list rather than showing an error about a group id.
 */
@Composable
fun HouseholdTab(
    currentGroupId: String?,
    onSelectGroup: (String) -> Unit,
    viewModel: ShellViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()

    val context = LocalContext.current
    val myUserId = remember {
        (context.applicationContext as? TaskFlowApp)?.tokenManager?.getUserId()
    }

    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    // The household a first-run starter list is being offered for, if any.
    var starterFor by remember { mutableStateOf<Group?>(null) }
    var starterError by remember { mutableStateOf<String?>(null) }

    when (val state = uiState) {
        is ShellUiState.Loading -> LoadingBlock("Loading your household…")

        is ShellUiState.Error -> GroupErrorBlock(
            message = state.message,
            onRetry = { viewModel.refresh() },
        )

        is ShellUiState.Success -> {
            val groups = state.groups
            val current = groups.firstOrNull { it.id == currentGroupId } ?: groups.firstOrNull()

            // Write the resolution back up, so a stored id that no longer names
            // a household is corrected once rather than re-resolved on every
            // recomposition — and so leaving a household lands somewhere real.
            LaunchedEffect(current?.id, currentGroupId) {
                current?.id?.takeIf { it != currentGroupId }?.let(onSelectGroup)
            }

            if (current == null) {
                NoHouseholdBlock(
                    onCreate = { showCreate = true },
                    onJoin = { showJoin = true },
                )
            } else {
                GroupDetailScreen(
                    // Keyed by household: the board's ViewModel is built for one
                    // group, so switching must give it a new one rather than
                    // leave the old group's chores on screen under a new name.
                    groupId = current.id,
                    groups = groups,
                    onSelectGroup = onSelectGroup,
                    onCreateHousehold = { showCreate = true },
                    onJoinHousehold = { showJoin = true },
                    // Leaving may have deleted the household outright. Clearing
                    // the selection sends the resolution above round again,
                    // which lands on another household or on the empty state.
                    onLeft = { viewModel.refresh() },
                )
            }
        }
    }

    if (showJoin) {
        JoinWithCodeDialog(
            onDismiss = { showJoin = false },
            onJoin = { code ->
                showJoin = false
                viewModel.redeemCode(code)
            },
        )
    }

    if (showCreate) {
        CreateGroupDialog(
            onDismiss = { showCreate = false },
            onGroupCreated = { group ->
                showCreate = false
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
                onSelectGroup(group.id)
            },
            onAdd = { chosen ->
                starterError = null
                viewModel.addStarterChores(group.id, myUserId, chosen) { failure ->
                    if (failure == null) {
                        starterFor = null
                        onSelectGroup(group.id)
                    } else {
                        starterError = failure
                    }
                }
            },
        )
    }
}

/**
 * No households at all — a first sign-in, or the last one just left.
 *
 * It offers both ways in rather than sending the person to Settings to find
 * half the answer: somebody with nothing to look at is the one person who must
 * not be given another screen to visit first (DESIGN.md §5.3).
 */
@Composable
private fun NoHouseholdBlock(onCreate: () -> Unit, onJoin: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Home,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "No household yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Start one for your flat, or join the one your housemates " +
                    "already have with the code they'll have been given.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Create a household")
            }
            TextButton(onClick = onJoin) {
                Icon(Icons.Default.VpnKey, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Join with a code")
            }
        }
    }
}
