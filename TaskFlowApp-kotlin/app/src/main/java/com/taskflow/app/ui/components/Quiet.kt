package com.taskflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskflow.app.ui.theme.Quiet

// The pieces every Quiet House screen is built from. Kept in one file so a
// screen cannot drift into its own version of the avatar or the dock.

/** Soft pastel, dark ink: one pair per person, picked from their id so it never changes. */
private val AvatarPairs = listOf(
    Color(0xFFE9D5F2) to Color(0xFF4B2A5C),
    Color(0xFFF8D7C8) to Color(0xFF6B2E14),
    Color(0xFFCFDDF6) to Color(0xFF1F3A6B),
    Color(0xFFD3EAD8) to Color(0xFF1F4A2C),
    Color(0xFFF6E7B8) to Color(0xFF5C4A10),
    Color(0xFFF6D0DA) to Color(0xFF6B1E33),
)

private fun avatarPair(id: String): Pair<Color, Color> =
    AvatarPairs[Math.floorMod(id.hashCode(), AvatarPairs.size)]

/**
 * A person, as an initial in their own colour. The same everywhere they
 * appear, so a face is recognised before a name is read.
 *
 * [dimmed] is for someone away: still there, quieter.
 */
@Composable
fun Avatar(
    name: String,
    id: String,
    size: Dp = 36.dp,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = avatarPair(id)
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (dimmed) 0.55f else 1f)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

/** A small sage tick on an avatar's corner: this person did it. */
@Composable
fun DoneAvatar(name: String, id: String, size: Dp = 36.dp) {
    Box {
        Avatar(name, id, size)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 3.dp, y = 3.dp)
                .size(size * 0.48f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(2.dp)
                .clip(CircleShape)
                .background(Quiet.colors.done),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.3f),
            )
        }
    }
}

/** Overlapping faces, ringed in the ground colour. Measures as wide as it draws. */
@Composable
fun AvatarStack(people: List<Triple<String, String, Boolean>>, size: Dp = 34.dp) {
    val step = size * 0.72f
    Box {
        people.forEachIndexed { i, (name, id, away) ->
            Box(
                modifier = Modifier
                    .padding(start = step * i)
                    .border(2.5.dp, MaterialTheme.colorScheme.background, CircleShape),
            ) {
                Avatar(name, id, size, dimmed = away)
            }
        }
    }
}

/** The small grey heading over a group of rows: "Yours", "Everyone else". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

/** Caps label inside a card: "WHAT DONE MEANS", "TAKES TURNS". */
@Composable
fun CapsLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
}

/** One white rounded card holding rows separated by hairlines. */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** A screen's title block: an optional small line over a large Bricolage title. */
@Composable
fun ScreenTitle(title: String, over: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        over?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.headlineLarge)
    }
}

/** The three places in the dock. */
enum class DockTab(val label: String, val icon: ImageVector) {
    MINE("Mine", Icons.Outlined.CalendarMonth),
    HOUSE("House", Icons.Outlined.Home),
    FEED("Feed", Icons.Outlined.ShowChart),
}

/**
 * The floating dock: three places in an ink pill, and Add beside it in the
 * accent. It floats over the content, which scrolls under a soft fade, so
 * every list needs [DOCK_CLEARANCE] of bottom padding.
 */
@Composable
fun FloatingDock(
    current: DockTab?,
    onSelect: (DockTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    tabModifier: (DockTab) -> Modifier = { Modifier },
    addModifier: Modifier = Modifier,
) {
    val q = Quiet.colors
    Box(modifier = modifier.fillMaxWidth()) {
        // The fade the content disappears into.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0f),
                        0.45f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(q.dock)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DockTab.entries.forEach { tab ->
                    val selected = tab == current
                    Column(
                        modifier = tabModifier(tab)
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(if (selected) q.dockActive else Color.Transparent)
                            .clickable(role = Role.Tab) { onSelect(tab) }
                            .semantics { this.selected = selected },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val tint = if (selected) q.onDock else q.onDockMuted
                        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(tab.label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Box(
                modifier = addModifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = "Add a chore" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * Every popup in the app, so none of them can lose its last line.
 *
 * Material's AlertDialog gives its text a bounded slot and silently clips
 * whatever does not fit. On a small screen or with a larger system font the end
 * of the text simply stopped at the buttons, with no sign that anything was
 * missing and no way to reach it: the away dialog lost "Everyone in the
 * household can see that you're away", which is the line that matters most.
 *
 * So the body always scrolls, and a rule above the buttons says there is more.
 * Use this rather than AlertDialog directly.
 */
@Composable
fun QuietAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = {
            Box {
                Column(modifier = Modifier.verticalScroll(scroll)) { text() }
                if (scroll.canScrollForward) {
                    HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        },
    )
}

/** Bottom padding a scrolling list needs so its last row clears the dock. */
val DOCK_CLEARANCE = 120.dp

/**
 * The toast: ink, rounded, the action in the accent's light tint. Used by
 * every SnackbarHost so the "… is next · Undo" moment looks the same
 * wherever it happens.
 */
@Composable
fun QuietSnackbar(data: SnackbarData) {
    val q = Quiet.colors
    Snackbar(
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = q.dock,
        contentColor = q.onDock,
        action = data.visuals.actionLabel?.let { label ->
            {
                TextButton(onClick = { data.performAction() }) {
                    Text(label, color = MaterialTheme.colorScheme.inversePrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) {
        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Width helper for an icon + label row inside a pill button. */
@Composable
fun IconLabel(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(label)
}

/** Two-way segmented control on a soft track: "Everything / Who did what". */
@Composable
fun SegmentedPair(first: String, second: String, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(first, second).forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** How long a toast stays: long enough to read "… is next" and still reach Undo. */
const val TOAST_MILLIS = 8_000L

/**
 * Show a toast for [TOAST_MILLIS]. Material only offers ~4 s (Short) or ~10 s
 * (Long), so this shows it indefinitely and takes it down on a timer;
 * cancelling the show is what dismisses it.
 */
suspend fun androidx.compose.material3.SnackbarHostState.showToast(
    message: String,
    actionLabel: String? = null,
): androidx.compose.material3.SnackbarResult =
    kotlinx.coroutines.withTimeoutOrNull(TOAST_MILLIS) {
        showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = androidx.compose.material3.SnackbarDuration.Indefinite,
        )
    } ?: androidx.compose.material3.SnackbarResult.Dismissed
