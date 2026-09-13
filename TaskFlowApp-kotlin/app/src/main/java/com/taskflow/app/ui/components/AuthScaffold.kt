package com.taskflow.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskflow.app.R

/**
 * The pieces the two auth screens share.
 *
 * Sign in and Create account are one design drawn twice: same emblem, same
 * wordmark, same field shape, and only the fields and the action differ. These
 * live here rather than in either screen so a change to the shape of a field
 * cannot land on one screen and miss the other.
 *
 * Ported from the Stitch mock (project "Core Design System", screens "Sign in"
 * and "Create account"), which is DESIGN.md §5.1–5.2 drawn.
 */

/**
 * Emblem, wordmark and subtitle — the top of both auth screens, identical on
 * each.
 */
@Composable
fun AuthHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark()

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "TaskFlow",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your household's chore board.",
            style = MaterialTheme.typography.bodyMedium,
            color = mutedInk(),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The emblem above the wordmark: the app's own launcher mark on a violet tile,
 * so the first screen a person sees carries the icon they tapped to get here.
 *
 * `ic_launcher_foreground` is an adaptive-icon foreground — a 108dp canvas of
 * which only the centre 72dp is the mark — so it is drawn oversized and clipped
 * to the tile, which brings the mark up to the size the tile wants.
 */
@Composable
fun BrandMark() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            // The drawable's own stroke is white, which is onPrimary in both
            // schemes today; tinting keeps the mark legible if that changes.
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
            modifier = Modifier.size(72.dp),
        )
    }
}

/**
 * A labelled field in the mock's shape: the label sits above the box rather
 * than floating inside it, the box is a 12dp-radius surface card with a hairline
 * outline, the icon leads, and [helper] — when there is one — explains the field
 * underneath in muted text.
 */
@Composable
fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    helper: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val muted = mutedInk()
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtleInk(),
                )
            },
            leadingIcon = {
                Icon(imageVector = leadingIcon, contentDescription = null, tint = muted)
            },
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (helper != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * The pill primary action, 56dp tall, with the spinner taking the label's place
 * while the call is in flight.
 */
@Composable
fun AuthSubmitButton(
    label: String,
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            // heightIn, not height: at 200% text the label has to be able to
            // push the pill taller instead of clipping.
            .heightIn(min = 56.dp),
        enabled = !isLoading,
        shape = CircleShape,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

/** The violet info callout — why you are looking at this screen. */
@Composable
fun NoticeCallout(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/**
 * A failed attempt, as a line under the fields.
 *
 * Amber, not colorScheme.error: DESIGN.md §5.1 asks for an amber inline line,
 * and the app uses red nowhere a person is being told they got something wrong.
 */
@Composable
fun AuthErrorLine(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = authAmber(),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The quiet reassurance pill at the foot of the sign-in screen. */
@Composable
fun QuietFooter(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Protected household synchronization",
                style = MaterialTheme.typography.labelSmall,
                color = mutedInk(),
            )
        }
    }
}

/**
 * The mock's ink-muted (#5B5D72) and ink-subtle (#8C8EA3), expressed as alpha
 * over onSurface rather than as literals so the dark scheme gets the same
 * relationship without a second pair of hex values.
 */
@Composable
fun mutedInk(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)

@Composable
fun subtleInk(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

// The same amber the board draws past-due dates in (GroupDetailScreen), kept
// identical here so "wrong password" and "this was wanted yesterday" read as the
// same register of message rather than two different alarms.
private val AmberOnLight = Color(0xFF9A6700)
private val AmberOnDark = Color(0xFFE0A534)

@Composable
fun authAmber(): Color =
    // Asks the theme, not the system: once appearance can be overridden in the
    // app, a phone in light mode showing the app in dark must not get the light
    // amber drawn onto a dark surface.
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) AmberOnDark else AmberOnLight
