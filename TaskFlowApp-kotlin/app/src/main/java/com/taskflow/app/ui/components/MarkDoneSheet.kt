package com.taskflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.taskflow.app.ui.theme.TaskFlowTheme

/**
 * "Did you actually do it?" — the sheet behind a row's tick.
 *
 * The board's checkbox used to mark done on a single tap. Two things were wrong
 * with that: a tap lands by accident in a scrolling list, and — more to the
 * point — the household's agreed **definition of done** lived only in the
 * occurrence detail sheet, which is exactly the screen nobody opens when they
 * are stood in the kitchen with wet hands. So the line that says what "done"
 * means now appears at the one moment it is the only question being asked, and
 * **the tick itself no longer completes anything**: it opens this.
 *
 * A bottom sheet rather than a centred dialog, because everything else this app
 * asks from a row — the occurrence detail, the pass, the day picker — arrives
 * from the bottom edge, near the thumb that tapped. A dialog in the middle of
 * the screen would read as an interruption; this reads as the row opening up.
 *
 * Deliberately not a verdict on the person: no lateness, no date arithmetic, no
 * "are you sure" scolding. It restates the agreement, asks once, and gets out
 * of the way. [doneLine] blank is a normal state — a household that never wrote
 * one down is not doing it wrong (F4), and the sheet says so plainly rather
 * than leaving a gap.
 *
 * Undo is untouched and still lives on the row: ticking asks, unticking does
 * not, because the person unticking has already had this conversation.
 *
 * @param title the chore's name, quoted back so the confirm is about a thing.
 * @param doneLine the agreed done-line, or null/blank when there is none.
 * @param doneLineLabel what the line above [doneLine] calls it. An occurrence
 *   carries an agreement ("What done means"); a legacy one-off carries only its
 *   own description, and labelling that as an agreement would invent one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkDoneSheet(
    title: String,
    doneLine: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    doneLineLabel: String = "What done means",
) {
    val line = doneLine?.trim().orEmpty()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scrolls for the same reason the dialogs do: a long done-line
                // at a large font would otherwise push the buttons off the
                // sheet, and they are the point of it.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A tinted disc from `primary`, not `primaryContainer` — the
                // container roles here are Material's baseline, not ours, and
                // one of them is a dark red (CLAUDE.md §7).
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                            .copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = "Mark this turn as done?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (line.isNotEmpty()) {
                // The agreement, given its own surface so it reads as a
                // quotation of something the household decided rather than as
                // this sheet's own copy.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = doneLineLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                Text(
                    text = "Done all of that? It'll show as done for the whole household.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "No agreed definition of done for this one yet. It's your " +
                        "call on whether it's finished. It'll show as done for the " +
                        "whole household.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Full-width primary, with the way out beneath it rather than
            // beside it: on a sheet the thumb lands at the bottom, and the two
            // answers are not equals.
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Mark it done") }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Not yet") }
        }
    }
}

@Preview(name = "With a done-line", showBackground = true)
@Composable
private fun MarkDoneSheetPreview() {
    TaskFlowTheme {
        MarkDoneSheet(
            title = "Kitchen floor",
            doneLine = "Swept and mopped, bin taken out, sink empty.",
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "No done-line", showBackground = true)
@Composable
private fun MarkDoneSheetNoLinePreview() {
    TaskFlowTheme {
        MarkDoneSheet(
            title = "Recycling",
            doneLine = "",
            onDismiss = {},
            onConfirm = {},
        )
    }
}
