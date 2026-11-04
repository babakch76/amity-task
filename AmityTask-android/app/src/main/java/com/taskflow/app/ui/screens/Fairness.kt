package com.taskflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taskflow.app.ui.components.Avatar
import com.taskflow.app.ui.components.GroupedCard
import com.taskflow.app.ui.components.ScreenTitle

// How turns stay fair: the turn rule worked through, case by case, for when
// somebody asks "but then whose turn is it?". The guide states the rules; this
// shows them happening, with one small flat (Maya, you, Sam, in that order).
//
// The worked turns are what nextTurn in chores.go does. If that rule changes,
// these change with it, and FairnessTest checks the counts still come out even.

/** One turn in a worked example: who did it, and what the app made of it. */
internal data class FairTurn(val who: String, val note: String)

internal data class FairCase(
    val title: String,
    val body: String,
    val turns: List<FairTurn> = emptyList(),
    val after: String? = null,
)

internal const val FAIR_MAYA = "Maya"
internal const val FAIR_YOU = "You"
internal const val FAIR_SAM = "Sam"

internal val FAIR_CASES = listOf(
    FairCase(
        title = "Someone does your turn",
        body = "You forgot, or never opened the app, and Maya did it for you. " +
            "Nobody has to pass anything. The app sees whose turn it was and who did it.",
        turns = listOf(
            FairTurn(FAIR_MAYA, "Maya's own turn."),
            FairTurn(FAIR_MAYA, "Your turn, done by Maya. You owe one, and Maya earns a skip."),
            FairTurn(FAIR_YOU, "The one you owe comes back to you."),
            FairTurn(FAIR_SAM, "As usual."),
            FairTurn(FAIR_YOU, "Maya's turn, but Maya uses the skip, so it moves on to you."),
            FairTurn(FAIR_SAM, "As usual."),
        ),
        after = "Six turns, two each. Maya did one early and got one back later.",
    ),
    FairCase(
        title = "You're too busy and pass it",
        body = "Swipe your turn left on House, or open it and tap Busy. It goes to the " +
            "next person and only they are told. It works the same as someone doing your turn.",
        turns = listOf(
            FairTurn(FAIR_MAYA, "Maya's own turn."),
            FairTurn(FAIR_SAM, "Your turn, passed to Sam. You owe one, and Sam earns a skip."),
            FairTurn(FAIR_YOU, "The one you owe comes back to you."),
            FairTurn(FAIR_MAYA, "Sam's turn, but Sam uses the skip, so it moves on to Maya."),
            FairTurn(FAIR_YOU, "As usual."),
            FairTurn(FAIR_SAM, "As usual."),
        ),
        after = "Six turns, two each.",
    ),
    FairCase(
        title = "Passing again and again",
        body = "Every pass is one more turn you owe. They come back to you one at a time, " +
            "oldest first. A pass goes to someone who isn't already owed a skip on that " +
            "chore, so the same person isn't asked every time.",
    ),
    FairCase(
        title = "Everyone is busy",
        body = "If everyone passes it on, it comes back to whoever passed first. They pick " +
            "the day, no later than when it would have come round anyway.",
    ),
    FairCase(
        title = "Going away",
        body = "Say you're away on People. Your turns skip you until you're back, and you " +
            "owe nothing for that time. A turn that's already yours goes to the next person " +
            "and comes back to you when you return. You come back in the same place in the order. " +
            "Turns you already owed wait for you.",
    ),
    FairCase(
        title = "Everyone is away",
        body = "Then the household is paused. Nothing can be passed and nothing goes late. " +
            "When someone's back, every date moves on by the days you were all away. " +
            "Chores on a fixed day keep their day.",
    ),
    FairCase(
        title = "Someone leaves",
        body = "They come off every chore, and any turn they had goes to the next person. " +
            "Nobody owes them anything. If they come back, they start at the end of the " +
            "line, and the turns they owed when they left are theirs first.",
    ),
    FairCase(
        title = "Someone new joins",
        body = "They go to the end of every chore you all share, so they don't get the very " +
            "next turn. Chores for just a few of you stay as they are. You can turn this off " +
            "in the household settings, under New people.",
    ),
    FairCase(
        title = "Helping with someone's turn",
        body = "Open their turn and tap I worked on this too. Your name goes beside theirs, " +
            "but it still counts as their turn, so nobody owes or earns anything.",
    ),
    FairCase(
        title = "Doing it late",
        body = "A chore that comes every few days counts from when it was last done, so " +
            "doing it late moves the next turn along. One on a fixed day rolls to the next " +
            "one, and it is still yours.",
    ),
    FairCase(
        title = "Marked done by mistake",
        body = "Whoever marked it can undo it for ten minutes with Mark as not done.",
    ),
)

@Composable
fun FairnessScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenTitle("How turns stay fair")
            Text(
                text = "Everyone does one turn per round. Here is how that holds when plans " +
                    "change. The examples use a flat of three, taking turns as Maya, you, then Sam.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            FAIR_CASES.forEach { case -> FairCaseCard(case) }
        }
    }
}

@Composable
private fun FairCaseCard(case: FairCase) {
    GroupedCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(case.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = case.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (case.turns.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                case.turns.forEachIndexed { i, turn -> FairTurnRow(i + 1, turn) }
            }
        }
        case.after?.let {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun FairTurnRow(number: Int, turn: FairTurn) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(20.dp)
                .padding(top = 6.dp),
        )
        // Fixed ids, so the three keep the same colours every time.
        Avatar(turn.who, "fair-${turn.who}", 30.dp)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(turn.who, style = MaterialTheme.typography.titleSmall)
            Text(
                text = turn.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
