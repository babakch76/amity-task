package com.taskflow.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.taskflow.app.data.model.MemberInfo
import com.taskflow.app.ui.components.Avatar
import com.taskflow.app.ui.components.RowDivider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The longest household name. Mirrors householdNameMax in groups.go. */
internal const val HOUSEHOLD_NAME_MAX = 40

/** Characters as a person counts them, so an emoji is one, as on the server. */
internal fun nameLength(name: String) = name.trim().let { it.codePointCount(0, it.length) }

private enum class SettingsPage { MENU, RENAME, HAND_OVER, HAND_OVER_AND_LEAVE, OWNER_LEAVING }

/**
 * A household's own settings: rename it, hand it over, leave it, remove it.
 *
 * A card that grows out of the gear that opened it, with its rows coming in
 * one after another, and that shrinks back into it on the way out. Each choice
 * that needs more than a tap opens in the same card rather than stacking a
 * second dialog on top.
 *
 * An owner leaving a household with people in it is the one case with a
 * question in it: somebody has to be left able to remove the household, so the
 * card asks whether to hand it over or remove it for everyone.
 *
 * Plain leaving and removing still go through the screen's confirm dialogs, so
 * there is one place that says what is lost.
 *
 * @param originX Where the gear is across the screen, 0 to 1.
 * @param startLeaving Open straight onto the owner's choice, for Leave on
 *   People. Only for an owner with people staying; anyone else leaving has
 *   nothing to choose and goes straight to the confirm.
 */
@Composable
fun HouseholdSettings(
    householdName: String,
    members: List<MemberInfo>,
    myUserId: String?,
    originX: Float,
    startLeaving: Boolean,
    onDismiss: () -> Unit,
    onRename: (name: String, onResult: (String?) -> Unit) -> Unit,
    onHandOver: (MemberInfo) -> Unit,
    onHandOverAndLeave: (MemberInfo) -> Unit,
    onLeave: () -> Unit,
    onRemove: () -> Unit,
) {
    val me = members.firstOrNull { it.id == myUserId }
    val iOwn = me?.role == ROLE_OWNER
    val others = members.filter { it.id != myUserId }
    val owner = members.firstOrNull { it.role == ROLE_OWNER }
    // Only an owner with people staying behind has anything to decide.
    val ownerLeaving = iOwn && others.isNotEmpty()

    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var page by remember {
        mutableStateOf(if (startLeaving && ownerLeaving) SettingsPage.OWNER_LEAVING else SettingsPage.MENU)
    }

    LaunchedEffect(Unit) {
        shown.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 380f))
    }

    // Shrinks back to the gear, then does what was chosen, so the next thing
    // (a confirm dialog, the board changing) never appears under a card that
    // is still on its way out.
    fun close(then: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch {
            shown.animateTo(0f, tween(durationMillis = 170, easing = FastOutSlowInEasing))
            onDismiss()
            then()
        }
    }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The window's own dim snaps on at full strength. This one fades with
        // the card.
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }

        BackHandler(enabled = page != SettingsPage.MENU && !(startLeaving && page == SettingsPage.OWNER_LEAVING)) {
            page = if (page == SettingsPage.HAND_OVER_AND_LEAVE) SettingsPage.OWNER_LEAVING else SettingsPage.MENU
        }

        val progress = shown.value
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.36f * progress.coerceIn(0f, 1f)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { close() },
                ),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 64.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = progress
                        scaleX = 0.4f + 0.6f * p
                        scaleY = 0.2f + 0.8f * p
                        alpha = p.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(originX, 0f)
                    }
                    // A tap on the card is not a tap on the scrim behind it.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .animateContentSize(),
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn(tween(180)))
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "settings page",
                ) { current ->
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    ) {
                        when (current) {
                            SettingsPage.MENU -> MenuPage(
                                householdName = householdName,
                                ownerLine = when {
                                    iOwn -> "You own this household"
                                    owner != null -> "${owner.username} owns this household"
                                    else -> null
                                },
                                iOwn = iOwn,
                                canHandOver = iOwn && others.isNotEmpty(),
                                onRename = { page = SettingsPage.RENAME },
                                onHandOver = { page = SettingsPage.HAND_OVER },
                                onLeave = {
                                    if (ownerLeaving) page = SettingsPage.OWNER_LEAVING else close(onLeave)
                                },
                                onRemove = { close(onRemove) },
                            )

                            SettingsPage.RENAME -> RenamePage(
                                current = householdName,
                                onBack = { page = SettingsPage.MENU },
                                onSave = { name, onResult ->
                                    onRename(name) { problem ->
                                        onResult(problem)
                                        if (problem == null) close()
                                    }
                                },
                            )

                            SettingsPage.HAND_OVER, SettingsPage.HAND_OVER_AND_LEAVE -> {
                                val andLeave = current == SettingsPage.HAND_OVER_AND_LEAVE
                                HandOverPage(
                                    householdName = householdName,
                                    others = others,
                                    andLeave = andLeave,
                                    onBack = {
                                        page = if (andLeave) SettingsPage.OWNER_LEAVING else SettingsPage.MENU
                                    },
                                    onConfirm = { member ->
                                        close { if (andLeave) onHandOverAndLeave(member) else onHandOver(member) }
                                    },
                                )
                            }

                            SettingsPage.OWNER_LEAVING -> OwnerLeavingPage(
                                householdName = householdName,
                                showBack = !startLeaving,
                                onBack = { page = SettingsPage.MENU },
                                onHandOver = { page = SettingsPage.HAND_OVER_AND_LEAVE },
                                onRemove = { close(onRemove) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuPage(
    householdName: String,
    ownerLine: String?,
    iOwn: Boolean,
    canHandOver: Boolean,
    onRename: () -> Unit,
    onHandOver: () -> Unit,
    onLeave: () -> Unit,
    onRemove: () -> Unit,
) {
    var row = 0
    ComesIn(row++) {
        Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp)) {
            Text(householdName, style = MaterialTheme.typography.headlineSmall)
            ownerLine?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    ComesIn(row++) {
        SettingsCard {
            SettingRow(Icons.Outlined.Edit, "Rename", onClick = onRename)
            if (canHandOver) {
                RowDivider()
                SettingRow(
                    Icons.Outlined.Key,
                    "Hand over to someone",
                    detail = "They'll be the one who can remove it",
                    onClick = onHandOver,
                )
            }
        }
    }
    ComesIn(row++) {
        SettingsCard(modifier = Modifier.padding(top = 12.dp)) {
            SettingRow(Icons.AutoMirrored.Outlined.Logout, "Leave household", onClick = onLeave)
            if (iOwn) {
                RowDivider()
                SettingRow(
                    Icons.Outlined.DeleteOutline,
                    "Remove household",
                    detail = "For everyone. This can't be undone.",
                    danger = true,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
private fun RenamePage(
    current: String,
    onBack: () -> Unit,
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

    PageHeader("Rename", onBack)
    OutlinedTextField(
        value = name,
        // Typing stops at the limit rather than letting someone write a long
        // name and only then be told.
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
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onBack, enabled = !saving) { Text("Cancel") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = save, enabled = canSave) { Text(if (saving) "Saving…" else "Save") }
    }
}

@Composable
private fun HandOverPage(
    householdName: String,
    others: List<MemberInfo>,
    andLeave: Boolean,
    onBack: () -> Unit,
    onConfirm: (MemberInfo) -> Unit,
) {
    var chosen by remember { mutableStateOf(others.singleOrNull()) }

    PageHeader(if (andLeave) "Who takes over?" else "Hand over $householdName", onBack)
    Text(
        if (andLeave) "They'll own $householdName after you leave, and be the one who can remove it."
        else "They'll own $householdName and be the one who can remove it. You stay in it as usual.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    SettingsCard {
        others.forEachIndexed { i, member ->
            if (i > 0) RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = chosen?.id == member.id,
                        role = Role.RadioButton,
                        onClick = { chosen = member },
                    )
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(member.username, member.id, 36.dp, dimmed = member.away)
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
    Button(
        onClick = { chosen?.let(onConfirm) },
        enabled = chosen != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .heightIn(min = 48.dp),
    ) {
        val who = chosen?.username
        Text(
            when {
                who == null -> "Choose someone"
                andLeave -> "Hand over to $who and leave"
                else -> "Make $who the owner"
            },
        )
    }
}

@Composable
private fun OwnerLeavingPage(
    householdName: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onHandOver: () -> Unit,
    onRemove: () -> Unit,
) {
    PageHeader("Before you go", onBack.takeIf { showBack })
    Text(
        "You own $householdName. Hand it to someone who's staying, or remove it for everyone.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    SettingsCard {
        SettingRow(
            Icons.Outlined.Key,
            "Hand it over, then leave",
            detail = "Its chores and history stay for everyone else",
            onClick = onHandOver,
        )
        RowDivider()
        SettingRow(
            Icons.Outlined.DeleteOutline,
            "Remove it for everyone",
            detail = "Its board, chores and history are deleted",
            danger = true,
            onClick = onRemove,
        )
    }
}

@Composable
private fun PageHeader(title: String, onBack: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(end = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // The card sits on the surface colour, so its groups take the page colour
    // to read as groups, the way the You tab's do.
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    detail: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Fades and lifts its content in, a little after the one before it. */
@Composable
private fun ComesIn(index: Int, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L + index * 55L)
        p.animateTo(1f, tween(durationMillis = 240, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = p.value
            translationY = (1f - p.value) * 14.dp.toPx()
        },
    ) { content() }
}
