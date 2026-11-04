package com.taskflow.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.taskflow.app.data.model.MemberInfo
import com.taskflow.app.ui.components.Avatar
import com.taskflow.app.ui.components.PopChoice
import com.taskflow.app.ui.components.PopMenu
import com.taskflow.app.ui.components.QuietAlertDialog
import com.taskflow.app.ui.components.Segmented
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The longest household name. Mirrors householdNameMax in groups.go. */
internal const val HOUSEHOLD_NAME_MAX = 40

/** Characters as a person counts them, so an emoji is one, as on the server. */
internal fun nameLength(name: String) = name.trim().let { it.codePointCount(0, it.length) }

private enum class Step { MENU, RENAME, NEW_PEOPLE, HAND_OVER, HAND_OVER_AND_LEAVE, OWNER_LEAVING }

/**
 * A household's own settings: rename it, hand it over, leave it, remove it.
 *
 * Tapping the gear pops a few round buttons out of it ([PopMenu]), each with
 * its name beside it, and the gear turns. Nothing else on the screen moves.
 * Whatever needs more than a tap opens a small dialog.
 *
 * Leaving and removing always ask first. Plain leaving and removing go through
 * the screen's own confirm dialogs ([onLeave], [onRemove]), so there is one
 * place that says what is lost. An owner leaving a household with people in it
 * is asked whether to hand it over or remove it, and handing over and leaving
 * asks once more before it happens.
 *
 * @param anchor The gear's centre on screen, for the buttons to come out of.
 *   Null opens straight onto [startLeaving]'s question, for Leave on People.
 * @param startLeaving Open on the owner's choice. Only for an owner with
 *   people staying; anyone else leaving goes straight to the confirm.
 */
@Composable
fun HouseholdSettings(
    householdName: String,
    members: List<MemberInfo>,
    myUserId: String?,
    anchor: Offset?,
    startLeaving: Boolean,
    onDismiss: () -> Unit,
    onRename: (name: String, onResult: (String?) -> Unit) -> Unit,
    addsNewMembers: Boolean,
    onSetAddNewMembers: (on: Boolean, onResult: (String?) -> Unit) -> Unit,
    onHandOver: (MemberInfo) -> Unit,
    onHandOverAndLeave: (MemberInfo) -> Unit,
    onLeave: () -> Unit,
    onRemove: () -> Unit,
) {
    val me = members.firstOrNull { it.id == myUserId }
    val iOwn = me?.role == ROLE_OWNER
    val others = members.filter { it.id != myUserId }
    // Only an owner with people staying behind has anything to decide.
    val ownerLeaving = iOwn && others.isNotEmpty()

    var step by remember {
        mutableStateOf(if (startLeaving || anchor == null) Step.OWNER_LEAVING else Step.MENU)
    }
    // Who an owner who is leaving chose to hand over to, waiting on "are you sure".
    var leavingTo by remember { mutableStateOf<MemberInfo?>(null) }

    when (step) {
        Step.MENU -> PopMenu(
            anchor = anchor ?: Offset.Zero,
            anchorIcon = Icons.Outlined.Settings,
            closeLabel = "Close household settings",
            turn = 90f,
            choices = buildList {
                add(PopChoice("Rename", Icons.Outlined.Edit) { step = Step.RENAME })
                add(PopChoice("New people", Icons.Outlined.GroupAdd) { step = Step.NEW_PEOPLE })
                if (ownerLeaving) add(PopChoice("Hand over", Icons.Outlined.Key) { step = Step.HAND_OVER })
                add(
                    PopChoice("Leave", Icons.AutoMirrored.Outlined.Logout) {
                        if (ownerLeaving) step = Step.OWNER_LEAVING else { onDismiss(); onLeave() }
                    },
                )
                if (iOwn) add(PopChoice("Remove", Icons.Outlined.DeleteOutline, danger = true) { onDismiss(); onRemove() })
            },
            onDismiss = onDismiss,
        )

        Step.RENAME -> RenameDialog(
            current = householdName,
            onDismiss = onDismiss,
            onSave = { name, onResult ->
                onRename(name) { problem ->
                    onResult(problem)
                    if (problem == null) onDismiss()
                }
            },
        )

        Step.NEW_PEOPLE -> NewPeopleDialog(
            current = addsNewMembers,
            onDismiss = onDismiss,
            onSave = { on, onResult ->
                onSetAddNewMembers(on) { problem ->
                    onResult(problem)
                    if (problem == null) onDismiss()
                }
            },
        )

        Step.HAND_OVER -> HandOverDialog(
            householdName = householdName,
            others = others,
            andLeave = false,
            onDismiss = onDismiss,
            onConfirm = { onDismiss(); onHandOver(it) },
        )

        Step.HAND_OVER_AND_LEAVE -> {
            val chosen = leavingTo
            if (chosen == null) {
                HandOverDialog(
                    householdName = householdName,
                    others = others,
                    andLeave = true,
                    onDismiss = onDismiss,
                    onConfirm = { leavingTo = it },
                )
            } else {
                QuietAlertDialog(
                    onDismissRequest = onDismiss,
                    title = { Text("Leave $householdName?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "${chosen.username} will own it, and you'll lose access to its board. " +
                                "Your turns go to the next person in line.",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { onDismiss(); onHandOverAndLeave(chosen) }) {
                            Text("Leave", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                )
            }
        }

        Step.OWNER_LEAVING -> QuietAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Before you go", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("You own $householdName. Hand it to someone who's staying, or remove it for everyone.")
                    ChoiceRow(Icons.Outlined.Key, "Hand it over, then leave") { step = Step.HAND_OVER_AND_LEAVE }
                    ChoiceRow(Icons.Outlined.DeleteOutline, "Remove it for everyone", danger = true) {
                        onDismiss()
                        onRemove()
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String, (String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val length = nameLength(name)
    val canSave = !saving && length in 1..HOUSEHOLD_NAME_MAX && name.trim() != current

    val save = {
        if (canSave) {
            saving = true
            onSave(name.trim()) { result ->
                saving = false
                problem = result
            }
        }
    }

    QuietAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Rename", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    // Typing stops at the limit rather than letting someone
                    // write a long name and only then be told.
                    onValueChange = {
                        if (it.codePointCount(0, it.length) <= HOUSEHOLD_NAME_MAX) {
                            name = it
                            problem = null
                        }
                    },
                    label = { Text("Household name") },
                    singleLine = true,
                    isError = problem != null,
                    supportingText = {
                        Row {
                            Text(problem.orEmpty(), modifier = Modifier.weight(1f))
                            Text("$length/$HOUSEHOLD_NAME_MAX")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Everyone in the household sees the new name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = save, enabled = canSave) { Text(if (saving) "Saving…" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}

/**
 * Whether someone joining goes into the turns on their own. On by default; a
 * household that likes to sort out who does what first can turn it off.
 */
@Composable
private fun NewPeopleDialog(
    current: Boolean,
    onDismiss: () -> Unit,
    onSave: (Boolean, (String?) -> Unit) -> Unit,
) {
    var on by remember { mutableStateOf(current) }
    var problem by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    QuietAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("New people", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "When someone joins, they can go to the end of every chore you all share. " +
                        "Chores for just a few of you stay as they are.",
                )
                Segmented(
                    options = listOf("Add them", "Don't add them"),
                    selected = if (on) 0 else 1,
                    onSelect = { on = it == 0; problem = null },
                )
                problem?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    saving = true
                    onSave(on) { result ->
                        saving = false
                        problem = result
                    }
                },
                enabled = !saving && on != current,
            ) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}

@Composable
private fun HandOverDialog(
    householdName: String,
    others: List<MemberInfo>,
    andLeave: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (MemberInfo) -> Unit,
) {
    var chosen by remember { mutableStateOf(others.singleOrNull()) }
    QuietAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (andLeave) "Who takes over?" else "Hand over $householdName", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    if (andLeave) "They'll own $householdName after you leave, and be the one who can remove it."
                    else "They'll own $householdName and be the one who can remove it. You stay in it as usual.",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                others.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = chosen?.id == member.id,
                                role = Role.RadioButton,
                                onClick = { chosen = member },
                            )
                            .heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(member.username, member.id, 32.dp, dimmed = member.away)
                        Text(
                            member.username,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        )
                        RadioButton(selected = chosen?.id == member.id, onClick = null)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { chosen?.let(onConfirm) }, enabled = chosen != null) {
                val who = chosen?.username
                Text(
                    when {
                        who == null -> "Choose someone"
                        andLeave -> "Next"
                        else -> "Make $who the owner"
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
