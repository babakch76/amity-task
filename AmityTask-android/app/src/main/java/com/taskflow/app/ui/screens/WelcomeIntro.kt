package com.taskflow.app.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Played once, straight after signing in or up, and never on an ordinary start:
// people open this app to get something done, so a signed-in launch goes
// straight to Mine. Four houses, roofs in the icon's four colours, slide
// together; their walls become the one white square and their roofs turn into
// the pinwheel. The geometry is the launcher icon's (ic_launcher_foreground.xml,
// 108 units): each chevron there is one roof, turned 0, 90, 180 or 270 degrees.
// A tap or Back skips it, and with system animations off it does not play.

private val GROUND = Color(0xFF2E1065)
private val ROOFS = listOf(Color.White, Color(0xFFC4B5FD), Color(0xFFA78BFA), Color(0xFF8B5CF6))
private val TURNS = listOf(0f, 90f, 180f, -90f)

/** Where each house starts, along one row, in icon units. */
private val STARTS = listOf(-1.5f, -0.5f, 0.5f, 1.5f).map { 54f + it * 52f }

/** A roof, apex at the origin: the icon's "M44,40 L60,26 L76,40". */
private val ROOF = listOf(Offset(-16f, 14f), Offset(0f, 0f), Offset(16f, 14f))

private const val PLAY_MS = 2000
private const val HOLD_MS = 450L

@Composable
fun WelcomeIntro(onDone: () -> Unit) {
    val context = LocalContext.current
    val onDoneNow by rememberUpdatedState(onDone)
    // A tap can land while the timer is also finishing; leave only once.
    var left by remember { mutableStateOf(false) }
    fun finish() {
        if (!left) {
            left = true
            onDoneNow()
        }
    }
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!animationsOff) {
            progress.animateTo(1f, tween(PLAY_MS, easing = LinearEasing))
            delay(HOLD_MS)
        }
        finish()
    }
    BackHandler { finish() }

    // Light clock and battery on the purple, then back to whatever the theme had.
    val view = LocalView.current
    DisposableEffect(Unit) {
        var host = view.context
        while (host is ContextWrapper && host !is Activity) host = host.baseContext
        val bars = (host as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = bars?.isAppearanceLightStatusBars
        val lightNav = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            if (bars != null && lightStatus != null && lightNav != null) {
                bars.isAppearanceLightStatusBars = lightStatus
                bars.isAppearanceLightNavigationBars = lightNav
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GROUND)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Skip",
                onClick = { finish() },
            )
            .semantics { contentDescription = "Amity Task" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = progress.value
            val move = ease(span(t, 0f, 0.85f))
            val turn = ease(span(t, 0.35f, 1f))
            val merge = ease(span(t, 0.45f, 0.9f))
            // The row is about 200 units wide; the finished mark settles a bit
            // above the middle, leaving room for the name under it.
            val unit = min(size.width * 0.9f / 200f, size.height * 0.3f / 108f)
            val origin = Offset(size.width / 2f - 54f * unit, size.height * 0.45f - 54f * unit)
            fun at(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)

            for (i in 0..3) {
                val wall = 1f - merge
                if (wall > 0f) {
                    drawRoundRect(
                        color = ROOFS[i],
                        topLeft = at(lerp(STARTS[i] - 11f, 46.5f, move), lerp(44f, 45.5f, move)),
                        size = Size(lerp(22f, 15f, move) * unit, lerp(18f, 17f, move) * unit),
                        cornerRadius = CornerRadius(3f * unit),
                        alpha = 0.9f * wall,
                    )
                }
            }
            drawRoundRect(
                color = Color.White,
                topLeft = at(46.5f, 45.5f),
                size = Size(15f * unit, 17f * unit),
                cornerRadius = CornerRadius(4.5f * unit),
                alpha = merge,
            )
            for (i in 0..3) {
                val end = finalApex(TURNS[i])
                val apex = Offset(lerp(STARTS[i], end.x, move), lerp(30f, end.y, move))
                val angle = Math.toRadians((TURNS[i] * turn).toDouble())
                val c = cos(angle).toFloat()
                val s = sin(angle).toFloat()
                val path = Path()
                ROOF.forEachIndexed { k, p ->
                    val point = at(apex.x + p.x * c - p.y * s, apex.y + p.x * s + p.y * c)
                    if (k == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(
                    path = path,
                    color = ROOFS[i],
                    style = Stroke(width = 9f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        Text(
            text = "Amity Task",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 200.dp)
                .graphicsLayer { alpha = ease(span(progress.value, 0.75f, 1f)) },
        )
    }
}

/** Where roof [turn] ends up: the icon's apex (60, 26), turned round the centre. */
private fun finalApex(turn: Float): Offset {
    val a = Math.toRadians(turn.toDouble())
    val x = 60f - 54f
    val y = 26f - 54f
    return Offset(
        54f + (x * cos(a) - y * sin(a)).toFloat(),
        54f + (x * sin(a) + y * cos(a)).toFloat(),
    )
}

private fun span(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Slow in, slow out: the cubic the prototype used. */
private fun ease(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
