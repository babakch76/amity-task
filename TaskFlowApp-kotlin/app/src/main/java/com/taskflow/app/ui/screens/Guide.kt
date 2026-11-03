package com.taskflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.taskflow.app.ui.components.GroupedCard
import com.taskflow.app.ui.components.ScreenTitle

// How TaskFlow works. The tour in Tour.kt shows where things are and is gone in a
// minute. This is the page to come back to when the question comes up, for the
// rules that take more than a sentence on a spotlight.

internal data class GuideSection(val title: String, val body: String)

/**
 * The turn rule is the part people need explained, so these have to stay true
 * to FEATURES.md sections 4 and 5. Change them together.
 */
internal val GUIDE = listOf(
    GuideSection(
        "Three places",
        "Mine is everything with your name on it. House is the whole board. " +
            "Feed shows what happened.",
    ),
    GuideSection(
        "Adding a chore",
        "Tap + and choose how often it comes round. Write down what done means " +
            "once, so nobody has to argue about it.",
    ),
    GuideSection(
        "Doing a chore",
        "Tap it, then I did this, or swipe your turn right on House. Anyone can " +
            "tick anything, so if you did someone else's, tap Mark as done. " +
            "You have ten minutes to undo.",
    ),
    GuideSection(
        "When someone covers for you",
        "If someone does your turn, it counts as theirs. The next one comes back " +
            "to you and they skip their next turn. Everyone still does one turn a round.",
    ),
    GuideSection(
        "Too busy",
        "Swipe your turn left on House, or open it and tap Busy. It goes to the " +
            "next person and only they are told. The next one comes back to you.",
    ),
    GuideSection(
        "If everyone is busy",
        "It comes back to whoever passed first. They pick the day, no later than " +
            "when it would have come round anyway.",
    ),
    GuideSection(
        "Going away",
        "Tap the faces on House and say you're away. Turns skip you and you owe " +
            "nothing for that time. Everyone can see you're away.",
    ),
    GuideSection(
        "Book it up",
        "Need the washer or the kitchen? Book it at the top of House and everyone " +
            "can see it. You'll be told about a clash, but it won't stop you, so " +
            "have a word with them.",
    ),
    GuideSection(
        "Planning ahead",
        "Tap the week on Mine to see the month. A ring means it's likely your " +
            "turn. Those can move if someone covers or passes, so check closer to the day.",
    ),
    GuideSection(
        "Reminders",
        "Your phone reminds you about your turns and tells you when one is passed " +
            "to you. Quiet hours, and news of housemates' chores, are behind your face on Mine.",
    ),
    GuideSection(
        "Bringing people in",
        "Tap the faces on House, then Share code.",
    ),
)

@Composable
fun GuideScreen(onBack: () -> Unit) {
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
            ScreenTitle("How Amity Task works", modifier = Modifier.padding(bottom = 8.dp))
            GUIDE.forEach { section ->
                GroupedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = section.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

