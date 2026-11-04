package com.taskflow.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.taskflow.app.ui.components.DockTab
import com.taskflow.app.ui.components.FloatingDock
import com.taskflow.app.ui.theme.Quiet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// The tour: the app's own screens with the lights down, one thing lit at a time
// and a sentence on what it does. It walks through Mine, House and Feed by itself.
//
// It shows a made-up flat (TourSample.kt) and not this person's account. On their
// own account it came out at 5, 11 or 12 steps depending on what they happened to
// have, and somebody with nothing yet saw the least. This way everybody gets the
// same walk, it can run before there is a household, and it cannot touch anything
// real. How TaskFlow works stays as the place to read the rules.

/** A part of the app the tour can light up. Marked where it is drawn, with [tourTarget]. */
internal enum class TourTarget {
    DOCK_MINE,
    DOCK_HOUSE,
    DOCK_FEED,
    ADD,
    WEEK,
    UP_NEXT,
    FACE,
    ROW,
    SWIPE_ROW,
    SWITCHER,
    FACES,
    BOOK,
    FEED_VIEWS,
}

/**
 * One stop. [target] null means no spotlight, with the words in the middle of the
 * screen. [alsoLit] picks out the dock tab of the page being introduced, so
 * people see how to get back to it.
 */
internal data class TourStep(
    val title: String,
    val body: String,
    val tab: DockTab,
    val target: TourTarget? = null,
    val alsoLit: TourTarget? = null,
    val practice: Boolean = false,
)

/** The whole walk. It is the same for everybody, every time. */
internal val TOUR = listOf(
    TourStep(
        title = "Welcome to Amity Task",
        body = "Here's a sample flat to show you around. It takes about a minute, " +
            "and nothing here touches your real chores.",
        tab = DockTab.MINE,
    ),
    TourStep(
        title = "Mine",
        body = "Your own turns, from every household you're in. The app opens here.",
        tab = DockTab.MINE,
        target = TourTarget.DOCK_MINE,
    ),
    TourStep(
        title = "The next 7 days",
        body = "A dot means you have a turn that day. Tap this card to see the whole month.",
        tab = DockTab.MINE,
        target = TourTarget.WEEK,
    ),
    TourStep(
        title = "Up next",
        body = "Your next turn, and what done means. Tap Mark as done when it's done. " +
            "Too busy? The arrows pass it on.",
        tab = DockTab.MINE,
        target = TourTarget.UP_NEXT,
    ),
    TourStep(
        title = "House",
        body = "Everything that needs doing in the household, and whose turn it is. " +
            "Tap a chore to open it, or a heading to fold it away.",
        tab = DockTab.HOUSE,
        target = TourTarget.ROW,
        alsoLit = TourTarget.DOCK_HOUSE,
    ),
    TourStep(
        title = "Swipe your turn",
        body = "On House, swipe your own turn left to pass it on, or right to mark " +
            "it done. The app checks with you first.",
        tab = DockTab.HOUSE,
        target = TourTarget.SWIPE_ROW,
        practice = true,
    ),
    TourStep(
        title = "Your household",
        body = "Tap the name to switch household, start a new one or join with a code.",
        tab = DockTab.HOUSE,
        target = TourTarget.SWITCHER,
    ),
    TourStep(
        title = "People",
        body = "Tap here to see everyone or invite someone. Going away? Say so " +
            "under See everyone, so turns skip you.",
        tab = DockTab.HOUSE,
        target = TourTarget.FACES,
    ),
    TourStep(
        title = "Book a space",
        body = "Need the washer or the kitchen? Book it here so everyone knows.",
        tab = DockTab.HOUSE,
        target = TourTarget.BOOK,
    ),
    TourStep(
        title = "Feed",
        body = "What's been happening in the household. Who did what shows it " +
            "person by person.",
        tab = DockTab.FEED,
        target = TourTarget.FEED_VIEWS,
        alsoLit = TourTarget.DOCK_FEED,
    ),
    TourStep(
        title = "Add a chore",
        body = "Tap + to add one. You choose how often it comes round and who takes turns.",
        tab = DockTab.FEED,
        target = TourTarget.ADD,
    ),
    TourStep(
        title = "That's everything",
        body = "Tap your face for settings, this tour and how turns work. " +
            "No household yet? Start or join one on House.",
        tab = DockTab.MINE,
        target = TourTarget.FACE,
    ),
)

/** Where everything is on screen, and what the tour is looking for right now. */
@Stable
internal class TourPlaces {
    val rects = mutableStateMapOf<TourTarget, Rect>()
    var origin by mutableStateOf(Offset.Zero)
    var current by mutableStateOf<TourTarget?>(null)
}

internal val LocalTour = staticCompositionLocalOf<TourPlaces?> { null }

/** Marks this as something the tour can light, and scrolls to it when it does. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.tourTarget(target: TourTarget, enabled: Boolean = true): Modifier =
    if (!enabled) {
        this
    } else {
        composed {
            val tour = LocalTour.current
            val requester = remember { BringIntoViewRequester() }
            val lit = tour?.current == target
            LaunchedEffect(lit) { if (lit) requester.bringIntoView() }
            DisposableEffect(tour, target) { onDispose { tour?.rects?.remove(target) } }
            bringIntoViewRequester(requester).onGloballyPositioned { tour?.rects?.set(target, it.boundsInRoot()) }
        }
    }

/**
 * The sample flat with the overlay on it. A page of its own in the shell, so it
 * has no dock of the real kind and Back here means back a step.
 */
@Composable
fun TourScreen(onClose: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = TOUR[index]
    val places = remember { TourPlaces() }
    SideEffect { places.current = step.target }

    BackHandler { if (index > 0) index -= 1 else onClose() }

    CompositionLocalProvider(LocalTour provides places) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned { places.origin = it.positionInRoot() },
        ) {
            // A screen reader should get the words, not a made-up flat.
            Box(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
                Crossfade(targetState = step.tab, label = "sample page") { SamplePage(it) }
                FloatingDock(
                    current = step.tab,
                    onSelect = {},
                    onAdd = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                    tabModifier = { tab ->
                        Modifier.tourTarget(
                            when (tab) {
                                DockTab.MINE -> TourTarget.DOCK_MINE
                                DockTab.HOUSE -> TourTarget.DOCK_HOUSE
                                DockTab.FEED -> TourTarget.DOCK_FEED
                            },
                        )
                    },
                    addModifier = Modifier.tourTarget(TourTarget.ADD),
                )
            }
            TourOverlay(
                step = step,
                number = index + 1,
                count = TOUR.size,
                places = places,
                onBack = { index -= 1 },
                onNext = { if (index == TOUR.lastIndex) onClose() else index += 1 },
                onClose = onClose,
            )
        }
    }
}

/**
 * The empty pointer handler is what keeps taps off the sample underneath, even
 * through the spotlight. It must not consume anything: a consumed move would
 * cancel a tap on the buttons above it.
 */
@Composable
private fun TourOverlay(
    step: TourStep,
    number: Int,
    count: Int,
    places: TourPlaces,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val found = step.target?.let { places.rects[it] }?.translate(-places.origin)

    // A page takes a moment to draw, and on a sideways phone the thing may be off
    // the bottom of it. Then the words go in the middle, on their own.
    var gaveUp by remember(number) { mutableStateOf(false) }
    LaunchedEffect(number) {
        delay(TARGET_WAIT_MILLIS)
        gaveUp = true
    }
    val ready = step.target == null || found != null || gaveUp

    val hole = found?.inflate(with(density) { HOLE_MARGIN.toPx() })
    var lastHole by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(hole) { hole?.let { lastHole = it } }
    val spot by animateRectAsState(
        targetValue = when {
            hole != null -> hole
            ready -> Rect(lastHole.center, Size.Zero)
            else -> lastHole
        },
        animationSpec = tween(SPOT_MOVE_MILLIS),
        label = "spotlight",
    )
    val also = step.alsoLit?.let { places.rects[it] }
        ?.translate(-places.origin)
        ?.inflate(with(density) { HOLE_MARGIN.toPx() })

    val pulse by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
        label = "ring alpha",
    )

    val appear = remember(number) { Animatable(0f) }
    LaunchedEffect(number, ready) { if (ready) appear.animateTo(1f, tween(BUBBLE_FADE_MILLIS)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawRect(SCRIM)
            listOfNotNull(spot.takeIf { it.width > 1f }, also).forEach { rect ->
                val corner = CornerRadius(minOf(rect.height / 2, HOLE_CORNER.toPx()))
                drawRoundRect(Color.Black, rect.topLeft, rect.size, corner, blendMode = BlendMode.Clear)
                // A neon edge, so what is being explained cannot be mistaken
                // for the card explaining it: a soft glow that breathes, laid
                // outside the hole so it never tints the thing being shown,
                // and a solid line on the edge itself. (It was a thin pale
                // ring that faded in and out, and got lost.)
                for ((width, strength) in GLOW_LAYERS) {
                    val w = width.toPx()
                    val grown = rect.inflate(w / 2)
                    drawRoundRect(
                        NEON.copy(alpha = strength * pulse),
                        grown.topLeft, grown.size, CornerRadius(corner.x + w / 2),
                        style = Stroke(w),
                    )
                }
                val line = NEON_LINE.toPx()
                val edge = rect.inflate(line / 2)
                drawRoundRect(NEON, edge.topLeft, edge.size, CornerRadius(corner.x + line / 2), style = Stroke(line))
            }
        }
        // This page starts below the status bar, so the dimming is carried up over it.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(SCRIM, Offset(0f, -places.origin.y), Size(size.width, places.origin.y))
        }
        BubblePlacement(hole = hole, keepClearOf = also) {
            TourBubble(
                step = step,
                number = number,
                count = count,
                onBack = onBack,
                onNext = onNext,
                onClose = onClose,
                modifier = Modifier.graphicsLayer { alpha = appear.value },
            )
        }
    }
}

/**
 * Below the lit part if the words fit there, otherwise above it, and in the middle
 * when nothing is lit. It stops short of [keepClearOf], the dock tab lit alongside.
 * If neither side has the room, as with a tall card in big text on a small phone,
 * it sits over the bottom of the lit part: the words matter more than seeing all
 * of it. Its words scroll past the height of the screen, so the buttons stay on it.
 */
@Composable
private fun BubblePlacement(hole: Rect?, keepClearOf: Rect?, content: @Composable () -> Unit) {
    val insets = WindowInsets.safeDrawing
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val gap = BUBBLE_GAP.roundToPx()
        val width = minOf(constraints.maxWidth - 2 * BUBBLE_GUTTER.roundToPx(), BUBBLE_MAX_WIDTH.roundToPx())
        val top = gap
        val bottom = constraints.maxHeight - insets.getBottom(this) - gap
        val floor = minOf(bottom, keepClearOf?.let { it.top.roundToInt() - gap } ?: bottom)
        val above = hole?.let { it.top.roundToInt() - gap - top } ?: 0
        val below = hole?.let { floor - it.bottom.roundToInt() - gap } ?: 0

        val bubble = measurables.single().measure(
            Constraints(minWidth = width, maxWidth = width, maxHeight = bottom - top),
        )
        val y = when {
            hole == null -> top + (bottom - top - bubble.height) / 2
            below >= bubble.height -> hole.bottom.roundToInt() + gap
            above >= bubble.height -> hole.top.roundToInt() - gap - bubble.height
            else -> bottom - bubble.height
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            bubble.place((constraints.maxWidth - width) / 2, y)
        }
    }
}

@Composable
private fun TourBubble(
    step: TourStep,
    number: Int,
    count: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = number == count
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        // A shade greyer than the screens it points at, so the card reads as
        // the tour talking rather than one more part of the app.
        color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, 0.07f),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(end = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "$number of $count · Sample flat",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    step.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    step.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (step.practice) SwipePractice()
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!last) TextButton(onClick = onClose) { Text("Skip") }
                Spacer(Modifier.weight(1f))
                if (number > 1) TextButton(onClick = onBack) { Text("Back") }
                Button(onClick = onNext) {
                    Text(
                        when {
                            last -> "Done"
                            number == 1 -> "Show me"
                            else -> "Next"
                        },
                    )
                }
            }
        }
    }
}

/**
 * A pretend turn to try the swipe on, drawn like the real one with the same words
 * behind it. It shows itself going each way until somebody touches it, and works
 * for someone with nothing of their own to swipe yet. It talks to nothing.
 */
@Composable
private fun SwipePractice() {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var touched by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf(PRACTICE_PROMPT) }
    var rowWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val nudge = with(density) { PRACTICE_NUDGE.toPx() }
    val trigger = with(density) { PRACTICE_TRIGGER.toPx() }

    LaunchedEffect(touched) {
        if (touched) return@LaunchedEffect
        while (true) {
            delay(PRACTICE_PAUSE_MILLIS)
            offset.animateTo(-nudge, tween(PRACTICE_SLIDE_MILLIS))
            delay(PRACTICE_PAUSE_MILLIS)
            offset.animateTo(0f, tween(PRACTICE_SLIDE_MILLIS))
            delay(PRACTICE_PAUSE_MILLIS)
            offset.animateTo(nudge, tween(PRACTICE_SLIDE_MILLIS))
            delay(PRACTICE_PAUSE_MILLIS)
            offset.animateTo(0f, tween(PRACTICE_SLIDE_MILLIS))
        }
    }

    val shape = RoundedCornerShape(18.dp)
    val goingRight = offset.value > 0f
    val tint = if (goingRight) Quiet.colors.done else MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PRACTICE_ROW_HEIGHT)
                .clip(shape)
                .background(tint.copy(alpha = 0.16f))
                .onSizeChanged { rowWidth = it.width }
                .semantics { contentDescription = "Practice turn. Swipe it left or right." }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(-rowWidth * 0.6f, rowWidth * 0.6f))
                        }
                    },
                    onDragStarted = { touched = true },
                    onDragStopped = {
                        when {
                            offset.value <= -trigger -> said = PRACTICE_PASSED
                            offset.value >= trigger -> said = PRACTICE_DONE
                        }
                        offset.animateTo(0f, spring())
                    },
                ),
        ) {
            Text(
                text = if (goingRight) "Done" else "Pass it",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = tint,
                modifier = Modifier
                    .align(if (goingRight) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(SAMPLE_TURN, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Your turn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            said,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            minLines = 2,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

internal const val PRACTICE_PROMPT = "Try it on this one. It's only practice."
internal const val PRACTICE_PASSED = "Passed on. The next person takes it, and your next turn comes back to you."
internal const val PRACTICE_DONE = "Done. The next turn goes to the next person."

/** The spotlight's edge: a bright violet that stands out on the dim in either theme. */
private val NEON = Color(0xFFC77DFF)
private val NEON_LINE = 3.5.dp
/** The glow round it, widest and faintest first. */
private val GLOW_LAYERS = listOf(18.dp to 0.10f, 11.dp to 0.18f, 6.dp to 0.32f)

private val SCRIM = Color.Black.copy(alpha = 0.62f)
private val HOLE_MARGIN = 6.dp
private val HOLE_CORNER = 26.dp
private val BUBBLE_GAP = 14.dp
private val BUBBLE_GUTTER = 16.dp
private val BUBBLE_MAX_WIDTH = 380.dp
private val PRACTICE_ROW_HEIGHT = 64.dp
private val PRACTICE_NUDGE = 88.dp
private val PRACTICE_TRIGGER = 56.dp

private const val TARGET_WAIT_MILLIS = 900L
private const val SPOT_MOVE_MILLIS = 320
private const val PULSE_MILLIS = 900
private const val BUBBLE_FADE_MILLIS = 220
private const val PRACTICE_PAUSE_MILLIS = 500L
private const val PRACTICE_SLIDE_MILLIS = 500
