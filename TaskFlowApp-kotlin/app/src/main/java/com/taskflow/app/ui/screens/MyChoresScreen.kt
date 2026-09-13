package com.taskflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskflow.app.data.model.Chore
import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.isOpen

/**
 * **My chores** — the second shell tab, and the only screen in the app that
 * crosses household lines.
 *
 * The board answers "whose turn is it"; this answers "what is on *me*", for
 * every household at once. A person in two flats had to open each board in
 * turn to assemble that list in their head, which is exactly the sort of thing
 * software is for.
 *
 * ### What it deliberately does not show
 *
 * Only your own turns. Never another member's name, date, or workload, and no
 * shared or comparison mode — DESIGN.md's never-list, and hard constraint 4
 * behind it. That also makes the rows simpler than the board's: the assignee
 * is always you, so no row has to say so.
 *
 * ### Why it reuses the board's row
 *
 * [TaskCard] is the board's own row, not a copy of it. The alternative was a
 * second row implementation that starts identical and drifts — the same
 * mistake the two auth screens made before `AuthScaffold` pulled them back
 * together. What differs is only what is *passed*: no swipe-to-pass here (the
 * gesture needs the rotation, which belongs to one household's board), and a
 * household name above each group of rows instead of an assignee on each one.
 *
 * The checkbox works, because "I did this" is the whole reason to look at this
 * list. Everything else — passing, away, the done-line, the history — lives on
 * the board, one tap away through the row.
 *
 * @param myUserId Whose turns these are. Null only if the token has no user id
 *   on it, in which case the list is honestly empty rather than everyone's.
 * @param onOpenGroup Opens the household's board, outside the shell.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyChoresScreen(
    myUserId: String?,
    onOpenGroup: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val reminderData by viewModel.reminderData.collectAsState()
    val me by viewModel.me.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()

    val groups = (uiState as? DashboardUiState.Success)?.groups.orEmpty()

    val households = remember(groups, reminderData, myUserId) {
        myChoresByHousehold(groups, reminderData, myUserId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My chores", fontWeight = FontWeight.Bold) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (households.isEmpty()) {
                // Not a failure, and worded so it does not read as one: an
                // empty list here is the good outcome.
                EmptyBlock(
                    title = "Nothing on you",
                    subtitle = "Turns with your name on them show up here, from every household you're in.",
                )
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    // Clears the bottom bar so the last row stays reachable.
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                households.forEach { household ->
                    item(key = "header-${household.group.id}") {
                        Text(
                            text = household.group.name.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                    }
                    items(household.occurrences, key = { "occ-${it.id}" }) { occurrence ->
                        val row = BoardRow.OccurrenceRow(occurrence)
                        TaskCard(
                            row = row,
                            scheduleText = scheduleLineFor(row, household.chores),
                            highlighted = false,
                            // Always yours, so the row never says so — and it
                            // certainly never names anybody else.
                            assigneeName = null,
                            // The only name this screen will print, and only
                            // on your own row: who actually ticked it. Your
                            // own turn covered by a flatmate is an event on
                            // your row, not a report on their workload — which
                            // is what the never-list is guarding against.
                            doneByName = if (occurrence.doneBy == myUserId) {
                                me?.username
                            } else {
                                occurrence.coveredByName
                            },
                            coveredByName = occurrence.coveredByName,
                            needsDate = occurrence.needsDate,
                            passedByMe = false,
                            // Passing needs the rotation and the household's
                            // away states, which belong to that board. The row
                            // opens it.
                            canPass = false,
                            myUserId = myUserId,
                            onOpenDetail = { onOpenGroup(household.group.id) },
                            onToggleDone = {
                                viewModel.setOccurrenceDone(
                                    groupId = household.group.id,
                                    occurrenceId = occurrence.id,
                                    done = occurrence.isOpen,
                                )
                            },
                            onPass = {},
                            onSwiped = {},
                        )
                    }
                    item(key = "gap-${household.group.id}") { Spacer(Modifier.height(6.dp)) }
                }
            }
        }
    }
}

/** One household's worth of your turns, with the chores they came from. */
private data class MyChoresHousehold(
    val group: Group,
    val occurrences: List<Occurrence>,
    val chores: List<Chore>,
)

/**
 * Your own turns, grouped by household and ordered the way the board orders
 * them: open first, oldest date first, then the ones already done this cycle.
 *
 * Done rows are kept rather than hidden, and kept last. They are what makes
 * the ten-minute undo reachable from this tab at all — a list that drops a
 * chore the instant you tick it gives a mis-tap nowhere to go.
 *
 * A household you have no turns in is left out entirely: an empty heading is
 * a row that says nothing.
 */
private fun myChoresByHousehold(
    groups: List<Group>,
    reminderData: List<DashboardViewModel.GroupReminderData>,
    myUserId: String?,
): List<MyChoresHousehold> {
    if (myUserId == null) return emptyList()
    return groups.mapNotNull { group ->
        val data = reminderData.firstOrNull { it.groupId == group.id } ?: return@mapNotNull null
        val mine = data.occurrences
            .filter { it.assignedTo == myUserId }
            .sortedWith(
                compareBy<Occurrence> { !it.isOpen }
                    // An as-needed turn has no date; it waits behind the dated
                    // ones rather than jumping the queue on a null.
                    .thenBy { it.dueDate == null }
                    .thenBy { it.dueDate },
            )
        if (mine.isEmpty()) null else MyChoresHousehold(group, mine, data.chores)
    }
}
