package com.taskflow.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
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
fun AuthHeader(
    modifier: Modifier = Modifier,
    title: String = "Whose turn\nis it?",
    subtitle: String = "One shared board for the people you live with. Turns go round on their own, so nobody has to nag.",
) {
    // Quiet House: the mark, then the one question the app answers, set large
    // and left-aligned, then a sentence on what it is.
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        // The name, said once, beside the mark it belongs to.
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(label = null)
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Amity Task",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The mark: the app's own launcher icon, in its own colours, so the first
 * screen and the home screen show the same thing. The foreground drawable is
 * an adaptive-icon layer of which a launcher shows the middle two thirds, so it
 * is drawn half as large again as the tile and clipped to it, showing the same. Not tinted to the theme: the chevrons' four
 * shades are the mark.
 *
 * @param label What a screen reader says; null when the name is written beside it.
 */
@Composable
fun BrandMark(size: androidx.compose.ui.unit.Dp = 64.dp, label: String? = "Amity Task") {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = label,
            modifier = Modifier.requiredSize(size * 1.5f),
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
            shape = RoundedCornerShape(16.dp),
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
