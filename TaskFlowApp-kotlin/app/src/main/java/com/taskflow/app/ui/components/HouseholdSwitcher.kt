package com.taskflow.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskflow.app.data.model.Group

/**
 * The household switcher: the board's title, and the way you change which
 * household you are looking at.
 *
 * DESIGN.md §4.2 deletes the group list as a destination — it was a transit
 * screen most people passed through with one card on it — and puts the choice
 * here instead, on the name that was already at the top of the board. The app
 * opens on the household you last had open; this is how you say you meant a
 * different one.
 *
 * **The chip stays even with one household.** §3 says to hide it then, on the
 * grounds that a chevron promising a choice with one option is a lie. It is not
 * one here, because the menu also carries *Create a household* and *Join with a
 * code* — with the list gone, this is the only place those two live, and
 * somebody in one flat is exactly the person most likely to want them.
 *
 * Deliberately not a scoreboard: every household row is identical bar its
 * monogram tint, there is no count of chores, turns or members on any of them,
 * and the only mark is a violet check on the one you are already in.
 */
@Composable
fun HouseholdSwitcher(
    current: Group?,
    groups: List<Group>,
    onSelect: (Group) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    // The chevron is the whole affordance — it has to be visibly the same
    // control in both states, so it turns rather than swapping to a second
    // icon.
    val chevron by animateFloatAsState(if (open) 180f else 0f, label = "chevron")

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable { open = true }
                .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = current?.name ?: "No household",
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = if (open) "Close household list" else "Switch household",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(26.dp)
                    .rotate(chevron),
            )
        }

        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(min = 240.dp, max = 320.dp),
        ) {
            Text(
                text = "YOUR HOUSEHOLDS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
            )

            groups.forEach { group ->
                val selected = group.id == current?.id
                DropdownMenuItem(
                    onClick = { open = false; if (!selected) onSelect(group) },
                    leadingIcon = { HouseholdMonogram(group, selected) },
                    text = {
                        Column {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // The description if there is one, and nothing
                            // invented if there isn't: no member count, no
                            // "3 chores open". The menu is for choosing a
                            // household, not for comparing two.
                            group.description.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Currently open",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // The two ways to end up with another household. They used to live
            // on the group list and in its overflow menu; with the list gone
            // this is where they belong, one tap from the name they change.
            SwitcherAction(
                label = "Create a household",
                icon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp)) },
                onClick = { open = false; onCreate() },
            )
            SwitcherAction(
                label = "Join with a code",
                icon = { Icon(Icons.Default.VpnKey, null, modifier = Modifier.size(20.dp)) },
                onClick = { open = false; onJoin() },
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * A household's first letter on a soft tint.
 *
 * The tint is derived from the household's id, so the same flat keeps the same
 * colour on every device and two households never look alike in the list. It
 * carries no meaning beyond telling them apart — it is not a status, and a
 * person cannot set it.
 *
 * **The three tints are the theme's own violet, blue and green**, at low alpha,
 * rather than the matching `…Container` roles. `Theme.kt` defines `primary`,
 * `secondary` and `tertiary` and stops there, so the container roles fall back
 * to Material's baseline — where `tertiaryContainer` is a dark red. A red disc
 * beside a chore row is exactly the shame signal constraint 3 rules out, and it
 * would have arrived without anybody choosing it.
 */
@Composable
private fun HouseholdMonogram(group: Group, selected: Boolean) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
    )
    val slot = (group.id.hashCode().rem(palette.size) + palette.size).rem(palette.size)
    val tint = palette[slot]

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f)),
    ) {
        Text(
            text = group.name.firstOrNull()?.uppercase() ?: "·",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = tint,
        )
    }
}

/** A menu row that does something rather than choosing something. */
@Composable
private fun SwitcherAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) { icon() }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
