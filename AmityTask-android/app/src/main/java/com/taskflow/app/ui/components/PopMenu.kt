package com.taskflow.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One of the buttons that pop out of an icon in the top right of House. */
data class PopChoice(
    val label: String,
    val icon: ImageVector,
    val danger: Boolean = false,
    val onPick: () -> Unit,
)

/**
 * Round buttons popping out of an icon, one a moment after another, with a
 * spring, down a gentle curve that leans away from the screen's edge. Each name
 * sits in a pill to the left of its button. Used by the gear (household
 * settings) and the people button, so both open the same way.
 *
 * (A tight arc round the corner was tried first: four named buttons do not fit
 * on it without overlapping.)
 *
 * They fold back into the icon on the way out, and only then is the choice
 * acted on, so a dialog never opens under buttons still moving. Picking calls
 * the choice's [PopChoice.onPick] *instead of* [onDismiss], because the next
 * step (a dialog) may belong to whoever opened this and must not be closed
 * first. Tapping away, the icon or Back calls [onDismiss].
 *
 * @param anchor The icon's centre on screen.
 * @param anchorIcon Drawn lifted above the dim where the icon was, as the way to close.
 * @param turn How far that icon turns while open, in degrees: a gear turns, people do not.
 */
@Composable
fun PopMenu(
    anchor: Offset,
    anchorIcon: ImageVector,
    closeLabel: String,
    choices: List<PopChoice>,
    onDismiss: () -> Unit,
    turn: Float = 0f,
) {
    val scope = rememberCoroutineScope()
    val shown = remember { Animatable(0f) }
    val pops = remember(choices.size) { List(choices.size) { Animatable(0f) } }
    var closing by remember { mutableStateOf(false) }
    // Where this window sits on screen, so the icon's position can be used in it.
    var origin by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(Unit) {
        launch { shown.animateTo(1f, tween(180)) }
        pops.forEachIndexed { i, a ->
            launch {
                delay(i * 45L)
                a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
            }
        }
    }

    fun close(picked: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        scope.launch {
            pops.reversed().forEachIndexed { i, a ->
                launch {
                    delay(i * 25L)
                    a.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
                }
            }
            shown.animateTo(0f, tween(220))
            if (picked == null) onDismiss() else picked()
        }
    }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The window's own dim snaps on at full strength. This one fades.
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }

        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionOnScreen() }
                .background(Color.Black.copy(alpha = 0.28f * shown.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { close() },
                ),
        ) {
            val at = origin ?: return@Box
            val centre = anchor - at
            val step = with(density) { 66.dp.toPx() }
            val lean = with(density) { 16.dp.toPx() }
            val rowWidth = with(density) { 240.dp.toPx() }
            val half = with(density) { 26.dp.toPx() }

            choices.forEachIndexed { i, choice ->
                val p = pops[i].value
                // Down from the icon, leaning a little further left each time.
                val x = centre.x - lean * i * p
                val y = centre.y + step * (i + 1) * p
                PopButton(
                    choice = choice,
                    modifier = Modifier
                        .offset {
                            // The button's centre lands on (x, y); its name
                            // hangs off to the left of it.
                            IntOffset((x + half - rowWidth).roundToInt(), (y - half).roundToInt())
                        }
                        .graphicsLayer {
                            scaleX = p.coerceAtLeast(0f)
                            scaleY = p.coerceAtLeast(0f)
                            alpha = p.coerceIn(0f, 1f)
                        },
                    onClick = { close(choice.onPick) },
                )
            }

            // The icon itself, lifted above the dim, so it reads as the thing
            // that opened them. Tapping it closes them again.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .offset {
                        val r = with(density) { 24.dp.toPx() }
                        IntOffset((centre.x - r).roundToInt(), (centre.y - r).roundToInt())
                    }
                    .size(48.dp)
                    .clickable { close() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        anchorIcon,
                        contentDescription = closeLabel,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(turn * shown.value),
                    )
                }
            }
        }
    }
}

@Composable
private fun PopButton(choice: PopChoice, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.width(240.dp),
    ) {
        // The name and the button are one target, so either can be tapped.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = scheme.surface,
                shadowElevation = 3.dp,
            ) {
                Text(
                    choice.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (choice.danger) scheme.error else scheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = if (choice.danger) scheme.errorContainer else scheme.primaryContainer,
                contentColor = if (choice.danger) scheme.onErrorContainer else scheme.onPrimaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(choice.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
