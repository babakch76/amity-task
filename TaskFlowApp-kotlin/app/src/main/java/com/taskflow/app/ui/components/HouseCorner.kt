package com.taskflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The top right of House: household settings, a people button and your own face.
 *
 * It used to be every housemate's face in a row. That said who lives here but
 * not what tapping it did, and grew with the household. Now the people button
 * pops out three buttons, as the gear does (see everyone, invite with a code,
 * invite by username),
 * and your face goes to You and settings, as it does on Mine, with the same dot
 * when an invitation is waiting. Who is away is still said on the board itself,
 * in the banner over it.
 *
 * The gear opens the household's own settings (rename, hand over, leave). It is
 * the household's, not yours, so it sits apart from your face.
 *
 * @param onHouseholdSettings Gets the gear's centre on screen, for the
 *   settings buttons to pop out of.
 * @param peopleModifier Lets the tour light the people button on its own.
 */
@Composable
fun HouseCorner(
    myName: String,
    myId: String,
    hasInvites: Boolean,
    onOpenSettings: () -> Unit,
    onSeeEveryone: () -> Unit,
    onShareCode: () -> Unit,
    onInviteByName: () -> Unit,
    onHouseholdSettings: (gearCentre: Offset) -> Unit,
    modifier: Modifier = Modifier,
    peopleModifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var gearCentre by remember { mutableStateOf(Offset.Zero) }
    var peopleCentre by remember { mutableStateOf(Offset.Zero) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onHouseholdSettings(gearCentre) },
            modifier = Modifier.onGloballyPositioned {
                gearCentre = it.positionOnScreen() + Offset(it.size.width / 2f, it.size.height / 2f)
            },
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "Household settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(modifier = peopleModifier) {
            IconButton(
                onClick = { open = true },
                modifier = Modifier.onGloballyPositioned {
                    peopleCentre = it.positionOnScreen() + Offset(it.size.width / 2f, it.size.height / 2f)
                },
            ) {
                Icon(
                    Icons.Outlined.Group,
                    contentDescription = "People in this household",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        // The same pop-out as the gear's, so the two corner icons open alike.
        if (open) {
            PopMenu(
                anchor = peopleCentre,
                anchorIcon = Icons.Outlined.Group,
                closeLabel = "Close",
                choices = listOf(
                    PopChoice("See everyone", Icons.Outlined.Group) { open = false; onSeeEveryone() },
                    PopChoice("Invite with a code", Icons.Default.Share) { open = false; onShareCode() },
                    PopChoice("Invite by username", Icons.Default.PersonAdd) { open = false; onInviteByName() },
                ),
                onDismiss = { open = false },
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onOpenSettings)
                .semantics { contentDescription = if (hasInvites) "You and settings, invitation waiting" else "You and settings" },
        ) {
            Avatar(myName, myId, 40.dp)
            if (hasInvites) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

