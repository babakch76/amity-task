package com.taskflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.taskflow.app.data.model.Chore
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.isOpen
import com.taskflow.app.ui.components.QuietAlertDialog
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.OffsetDateTime

/**
 * Everything that opens when you tap one of your turns, wherever you tapped it.
 *
 * The detail sheet, the chore's history, the edit flow behind it, the delete
 * confirm behind that, and the busy-pass confirm — one implementation, used by
 * both the board and **My chores**.
 *
 * It used to live inline in `GroupDetailScreen`, which was fine while the board
 * was the only place a chore could be opened from. My chores now opens the same
 * sheet, and copying two hundred lines of wiring to do it is how two screens
 * start agreeing and end up disagreeing — the argument that produced
 * `AuthScaffold` and that keeps `TaskCard` shared.
 *
 * Everything here is scoped to **one household**: the [GroupDetailViewModel] is
 * what performs the pass, the edit and the delete, and it is built for a single
 * group. My chores therefore opens one for whichever household the tapped turn
 * belongs to.
 *
 * @param controller What is open. Hoisted, because the board opens the sheet
 *   from a row tap and the pass confirm from a swipe, and both live outside
 *   this composable.
 */
@Composable
internal fun OccurrenceDetailHost(
    controller: ChoreDetailController,
    viewModel: GroupDetailViewModel,
    myUserId: String?,
    /** Shown as a chip on the sheet where the rows are not all one household's. */
    householdName: String? = null,
) {
    val state by viewModel.uiState.collectAsState()

    // Hold the *id* and re-derive the occurrence on each recomposition, so the
    // sheet follows a teammate completing it rather than showing the snapshot
    // it opened with.
    val occurrence = controller.occurrenceId?.let { id ->
        state.occurrences.firstOrNull { it.id == id }
    }

    // Same pattern for the chore being edited: a concurrent edit by someone
    // else is reflected rather than overwritten from a stale snapshot.
    val editChore = controller.editChoreId?.let { id ->
        state.chores.firstOrNull { it.id == id }
    }

    // Per-form submit state, so the edit flow can stay open until the write
    // lands and show a rejection where the user is already looking (B-7).
    var choreSubmitting by remember { mutableStateOf(false) }
    var choreError by remember { mutableStateOf<String?>(null) }

    // Whether the person looking at the sheet may still take the completion
    // back: they marked it, and the ten-minute window has not closed.
    val mineToUndo = occurrence != null && !occurrence.isOpen &&
        occurrence.doneBy != null && occurrence.doneBy == myUserId
    val canUndo = openUntil(occurrence?.doneAt?.plus(UNDO_WINDOW)?.takeIf { mineToUndo })

    // "I worked on this too": somebody else's finished turn that you are not
    // already on, for two days after it was done; and taking your own name off
    // again, for ten minutes after adding it.
    val myAlso = occurrence?.alsoBy.orEmpty().firstOrNull { it.userId == myUserId }
    val mayAddAlso = occurrence != null && !occurrence.isOpen && myUserId != null &&
        occurrence.doneBy != myUserId && myAlso == null
    val canAddAlso = openUntil(occurrence?.doneAt?.plus(ALSO_WINDOW)?.takeIf { mayAddAlso })
    val canUndoAlso = openUntil(myAlso?.addedAt?.plus(UNDO_WINDOW))

    occurrence?.let { occ ->
        OccurrenceDetailSheet(
            occurrence = occ,
            chore = state.chores.firstOrNull { it.id == occ.choreId },
            members = state.members,
            myUserId = myUserId,
            // The chip is for My chores, where the rows come from more than one
            // household. On the board the app bar has already said which.
            householdName = householdName,
            canUndo = canUndo,
            canAddAlso = canAddAlso,
            canUndoAlso = canUndoAlso,
            onDismiss = { controller.occurrenceId = null },
            // Marking done closes the sheet: the decision has been taken, and
            // the board behind it is where the result belongs. Undo leaves the
            // sheet open — it is a correction, and the sheet is where you check
            // that the correction landed.
            onMarkDone = {
                controller.occurrenceId = null
                viewModel.toggleOccurrenceDone(occ, myUserId)
            },
            onUndoDone = { viewModel.toggleOccurrenceDone(occ) },
            onAddAlso = { viewModel.addAlso(occ) },
            onUndoAlso = { viewModel.removeAlso(occ) },
            onPass = {
                controller.occurrenceId = null
                controller.passCandidate = occ
            },
            onPickDay = { picked ->
                controller.occurrenceId = null
                viewModel.setOccurrenceDueDate(occ.id, picked)
            },
            onEditChore = {
                // Close the sheet first: an AlertDialog over a bottom sheet
                // stacks two surfaces and the sheet's scrim fights the dialog's.
                controller.occurrenceId = null
                controller.editChoreId = occ.choreId
            },
            onShowHistory = {
                controller.occurrenceId = null
                controller.historyChoreName = occ.choreName
                viewModel.loadChoreHistory(occ.choreId)
            },
        )
    }

    editChore?.let { chore ->
        CreateChoreFlow(
            members = state.members,
            myUserId = myUserId,
            submitting = choreSubmitting,
            serverError = choreError,
            onDismiss = { controller.editChoreId = null; choreError = null },
            initial = chore.toDraft(),
            onDelete = { controller.choreToDelete = chore },
            // Only what actually changed goes up. Restating an untouched field
            // would overwrite a concurrent edit to it and would put a change
            // nobody made into the diff the whole group reads.
            onSubmit = { draft ->
                choreSubmitting = true
                choreError = null
                val before = chore.toDraft()
                val typeChanged = draft.kind != before.kind
                viewModel.updateChore(
                    choreId = chore.id,
                    name = draft.name.trim().takeIf { it != chore.name },
                    doneLine = draft.doneLine.trim().takeIf { it != chore.doneLine },
                    // On a type change the new type's parameters always go with
                    // it: the server clears the old type's columns, so leaving
                    // these out would produce a chore of the new kind with
                    // nothing to schedule from.
                    intervalDays = draft.intervalDays.takeIf {
                        draft.kind == ChoreKind.INTERVAL &&
                            (typeChanged || it != before.intervalDays)
                    },
                    fixedWeekdays = listOf(draft.weekday).takeIf {
                        draft.kind == ChoreKind.FIXED_DATE && draft.byWeekday &&
                            (typeChanged || !before.byWeekday || draft.weekday != before.weekday)
                    },
                    rotation = draft.rotation.takeIf { it != before.rotation },
                    scheduleType = draft.kind?.scheduleType.takeIf { typeChanged },
                    fixedMonthDays = listOf(draft.monthDay).takeIf {
                        draft.kind == ChoreKind.FIXED_DATE && !draft.byWeekday &&
                            (typeChanged || before.byWeekday || draft.monthDay != before.monthDay)
                    },
                    neededByTime = draft.neededBy?.takeIf { it != before.neededBy },
                ) { failure ->
                    choreSubmitting = false
                    if (failure == null) controller.editChoreId = null else choreError = failure
                }
            },
        )
    }

    // Deleting a chore takes every occurrence with it, and with them the record
    // of who did it — so it confirms, and the confirm says that rather than
    // asking "are you sure?". There is no undelete endpoint and a snackbar that
    // pretended otherwise would be a lie.
    controller.choreToDelete?.let { chore ->
        QuietAlertDialog(
            onDismissRequest = { controller.choreToDelete = null },
            title = { Text("Delete ${chore.name}?") },
            text = {
                Text(
                    "This removes the chore, whoever's turn it currently is, and " +
                        "the history of every time it has been done. It cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        controller.choreToDelete = null
                        controller.editChoreId = null
                        viewModel.deleteChore(chore.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { controller.choreToDelete = null }) { Text("Cancel") }
            },
        )
    }

    // An occurrence can vanish under the sheet: undoing a completion deletes
    // the one it spawned. Close rather than leave a sheet describing a row that
    // no longer exists.
    //
    // Only once it has been *seen*, though. My chores opens this with a
    // household ViewModel created on the spot, whose list is empty for the
    // first frame or two; treating "not loaded yet" as "gone" closed every
    // sheet My chores opened before it could draw, so tapping a turn there did
    // nothing at all.
    var seen by remember(controller.occurrenceId) { mutableStateOf(false) }
    LaunchedEffect(controller.occurrenceId, state.occurrences) {
        val id = controller.occurrenceId ?: return@LaunchedEffect
        if (state.occurrences.any { it.id == id }) {
            seen = true
        } else if (seen) {
            controller.occurrenceId = null
        }
    }

    // The busy pass, confirmed.
    //
    // The debt rule is stated twice by design: here, in plain language, at the
    // moment of passing; and afterwards as a standing marker on the row that
    // comes back. Once is not enough for a rule this counter-intuitive — the
    // chore leaves your row and returns a cycle later, which looks like a bug
    // unless somebody said so.
    controller.passCandidate?.let { occ ->
        val chore = state.chores.firstOrNull { it.id == occ.choreId }
        val receiver = passReceiver(occ, state.members)
        if (receiver == null) {
            // Nobody to pass to. Nothing to confirm, so say why rather than
            // showing a dialog with an empty name in it.
            controller.passCandidate = null
        } else {
            QuietAlertDialog(
                onDismissRequest = { controller.passCandidate = null },
                title = { Text("Pass ${occ.choreName} to ${receiver.username}?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Next time it's yours again. ${receiver.username}'s cover " +
                                "counts as ${receiver.username}'s turn.",
                        )
                        Text(passNoticeLine(occ, chore, receiver.username))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        controller.passCandidate = null
                        viewModel.passOccurrence(occ, receiver.username)
                    }) { Text("Pass it to ${receiver.username}") }
                },
                dismissButton = {
                    TextButton(onClick = { controller.passCandidate = null }) { Text("Keep it") }
                },
            )
        }
    }

    controller.historyChoreName?.let { name ->
        ChoreHistorySheet(
            choreName = name,
            history = state.choreHistory,
            loading = state.historyLoading,
            onDismiss = { controller.historyChoreName = null; viewModel.clearHistory() },
        )
    }
}

/**
 * True until [deadline], then false; false throughout when it is null.
 *
 * The clock cannot be read during composition — that freezes the answer at
 * whatever it was when the sheet was drawn, which is how the row's checkbox
 * once stayed enabled long past its window (B-6). So each window is waited
 * out instead: one coroutine, sleeping exactly until the moment it lapses.
 */
@Composable
private fun openUntil(deadline: OffsetDateTime?): Boolean {
    var open by remember(deadline) { mutableStateOf(false) }
    LaunchedEffect(deadline) {
        val remaining = deadline?.let { Duration.between(OffsetDateTime.now(), it) }
        if (remaining == null || remaining.isNegative || remaining.isZero) {
            open = false
            return@LaunchedEffect
        }
        open = true
        delay(remaining.toMillis())
        open = false
    }
    return open
}

/**
 * What [OccurrenceDetailHost] currently has open.
 *
 * Hoisted out of the host because the things that *open* these surfaces are
 * elsewhere: a board row tap, a swipe on a row, a row on My chores. One object
 * rather than five `mutableStateOf`s passed down, so adding a surface later
 * does not change every call site.
 */
@Stable
internal class ChoreDetailController {
    /** The occurrence whose detail sheet is open, if any. */
    var occurrenceId by mutableStateOf<String?>(null)

    /** The chore being edited in the full-screen flow, if any. */
    var editChoreId by mutableStateOf<String?>(null)

    /** The chore awaiting a delete confirm, if any. */
    var choreToDelete by mutableStateOf<Chore?>(null)

    /** The occurrence a busy pass is being confirmed for, if any. */
    var passCandidate by mutableStateOf<Occurrence?>(null)

    /**
     * Whose history is open. The name is held rather than the id so the sheet
     * has a title before its data arrives.
     */
    var historyChoreName by mutableStateOf<String?>(null)

    fun open(occurrenceId: String) {
        this.occurrenceId = occurrenceId
    }

    fun confirmPass(occurrence: Occurrence) {
        passCandidate = occurrence
    }
}

@Composable
internal fun rememberChoreDetailController(): ChoreDetailController =
    remember { ChoreDetailController() }
