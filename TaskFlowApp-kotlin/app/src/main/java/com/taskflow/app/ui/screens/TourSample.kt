package com.taskflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.taskflow.app.data.model.ActivityEvent
import com.taskflow.app.data.model.Booking
import com.taskflow.app.data.model.Chore
import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.MemberInfo
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.ScheduleType
import com.taskflow.app.ui.components.Avatar
import com.taskflow.app.ui.components.HouseCorner
import com.taskflow.app.ui.components.DOCK_CLEARANCE
import com.taskflow.app.ui.components.DockTab
import com.taskflow.app.ui.components.FoldLabel
import com.taskflow.app.ui.components.GroupedCard
import com.taskflow.app.ui.components.HouseholdSwitcher
import com.taskflow.app.ui.components.RowDivider
import com.taskflow.app.ui.components.ScreenTitle
import com.taskflow.app.ui.components.SectionLabel
import com.taskflow.app.ui.components.SegmentedPair
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// The made-up flat the tour walks through. It is the app's own composables given
// fixed data, so it cannot drift from what they look like, and there is no
// ViewModel or network behind any of it. The overlay stops anything being tapped.

internal const val SAMPLE_TURN = "Clean the bathroom"

private const val FLAT_ID = "sample-flat"
private const val ME = "you"
private const val MAYA = "maya"
private const val SAM = "sam"

/** Everything the sample pages show, dated from [today] so it never looks stale. */
internal class SampleFlat(val today: LocalDate, private val zone: ZoneId = ZoneId.systemDefault()) {
    private fun on(day: LocalDate, time: LocalTime = LocalTime.of(23, 59)): OffsetDateTime =
        day.atTime(time).atZone(zone).toOffsetDateTime()

    val group = Group(id = FLAT_ID, name = "Sample Flat", description = "", createdBy = ME)

    val members = listOf(
        MemberInfo(id = ME, username = "You", email = "", role = "owner"),
        MemberInfo(id = MAYA, username = "Maya", email = "", role = "member"),
        MemberInfo(id = SAM, username = "Sam", email = "", role = "member"),
    )

    private fun chore(id: String, name: String, doneLine: String) = Chore(
        id = id,
        groupId = FLAT_ID,
        name = name,
        doneLine = doneLine,
        scheduleType = ScheduleType.INTERVAL,
        intervalDays = 7,
        rotation = listOf(ME, MAYA, SAM),
        createdBy = ME,
    )

    val chores = listOf(
        chore("bathroom", SAMPLE_TURN, "Sink, loo and shower wiped, and the bin emptied"),
        chore("floor", "Kitchen floor", "Swept and mopped"),
        chore("bins", "Put the bins out", "Out on the kerb the night before"),
    )

    private fun turn(choreIndex: Int, who: String, due: LocalDate) = Occurrence(
        id = "turn-$choreIndex",
        choreId = chores[choreIndex].id,
        groupId = FLAT_ID,
        assignedTo = who,
        status = "open",
        dueDate = on(due),
        doneLine = chores[choreIndex].doneLine,
        choreName = chores[choreIndex].name,
        passTo = MAYA,
    )

    val upNext = MyTurn(turn(0, ME, today.plusDays(2)), group, chores)
    val alsoYours = MyTurn(turn(1, ME, today.plusDays(5)), group, chores)
    val maya = MyTurn(turn(2, MAYA, today.plusDays(3)), group, chores)

    val booking = Booking(
        id = "booking",
        groupId = FLAT_ID,
        userId = MAYA,
        username = "Maya",
        what = "Washing machine",
        startsAt = on(today.plusDays(1), LocalTime.of(9, 0)),
        endsAt = on(today.plusDays(1), LocalTime.of(11, 0)),
    )

    private fun event(id: String, who: String, name: String, type: String, detail: String, day: LocalDate) =
        ActivityEvent(
            id = id,
            groupId = FLAT_ID,
            actorId = who,
            actorUsername = name,
            eventType = type,
            detail = detail,
            createdAt = on(day, LocalTime.of(10, 0)),
        )

    val events = listOf(
        event("e1", SAM, "Sam", "occurrence_done", "Put the bins out", today),
        event("e2", MAYA, "Maya", "booking_added", "Washing machine", today),
        event("e3", ME, "You", "occurrence_done", "Kitchen floor", today.minusDays(1)),
    )
}

@Composable
internal fun SamplePage(tab: DockTab) {
    val flat = remember { SampleFlat(LocalDate.now()) }
    when (tab) {
        DockTab.MINE -> SampleMine(flat)
        DockTab.HOUSE -> SampleHouse(flat)
        DockTab.FEED -> SampleFeed(flat)
    }
}

/** Laid out as [MyChoresScreen] is. */
@Composable
private fun SampleMine(flat: SampleFlat) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = DOCK_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    flat.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Your turns", style = MaterialTheme.typography.headlineLarge)
            }
            Avatar("You", ME, 44.dp, modifier = Modifier.tourTarget(TourTarget.FACE))
        }
        Box(modifier = Modifier.tourTarget(TourTarget.WEEK)) {
            NextSevenDays(
                today = flat.today,
                days = setOfNotNull(flat.upNext.date, flat.alsoYours.date),
                onExpand = {},
            )
        }
        Box(modifier = Modifier.tourTarget(TourTarget.UP_NEXT)) {
            UpNextCard(turn = flat.upNext, showHousehold = true, onOpen = {}, onDone = {})
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Also yours")
            GroupedCard { TurnRow(flat.alsoYours, flat.today, showHousehold = false) {} }
        }
    }
}

/** Laid out as the board in [GroupDetailScreen] is. */
@Composable
private fun SampleHouse(flat: SampleFlat) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.tourTarget(TourTarget.SWITCHER)) {
                HouseholdSwitcher(
                    current = flat.group,
                    groups = listOf(flat.group),
                    onSelect = {},
                    onCreate = {},
                    onJoin = {},
                )
            }
            Spacer(Modifier.weight(1f))
            HouseCorner(
                myName = "You",
                myId = ME,
                hasInvites = false,
                onOpenSettings = {},
                onSeeEveryone = {},
                onShareCode = {},
                onInviteByName = {},
                peopleModifier = Modifier.tourTarget(TourTarget.FACES),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = DOCK_CLEARANCE),
        ) {
            BookItSection(
                bookings = listOf(flat.booking),
                myUserId = ME,
                onBook = {},
                onCancel = {},
                modifier = Modifier.tourTarget(TourTarget.BOOK),
            )
            SectionLabel("Yours", Modifier.padding(top = 14.dp, bottom = 8.dp))
            SampleRow(flat, flat.upNext, first = true, last = false, Modifier.tourTarget(TourTarget.SWIPE_ROW))
            SampleRow(flat, flat.alsoYours, first = false, last = true)
            // Lit together, so the House step can say a heading folds.
            Column(modifier = Modifier.tourTarget(TourTarget.ROW)) {
                FoldLabel("Everyone else", count = 1, folded = false, onToggle = {}, modifier = Modifier.padding(top = 6.dp))
                SampleRow(flat, flat.maya, first = true, last = true)
            }
        }
    }
}

@Composable
private fun SampleRow(flat: SampleFlat, turn: MyTurn, first: Boolean, last: Boolean, modifier: Modifier = Modifier) {
    val row = BoardRow.OccurrenceRow(turn.occurrence)
    Box(modifier = modifier) {
        TaskCard(
            row = row,
            first = first,
            last = last,
            scheduleText = scheduleLineFor(row, flat.chores),
            highlighted = false,
            assigneeName = flat.members.firstOrNull { it.id == row.assignedTo }?.username,
            doneByName = null,
            coveredByName = null,
            needsDate = false,
            passedByMe = false,
            canPass = true,
            myUserId = ME,
            onOpenDetail = {},
            onToggleDone = {},
            onPass = {},
            onSwiped = {},
        )
    }
}

/** Laid out as the feed in [GroupDetailScreen] is. */
@Composable
private fun SampleFeed(flat: SampleFlat) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenTitle(title = "What happened", over = flat.group.name)
            Box(modifier = Modifier.tourTarget(TourTarget.FEED_VIEWS)) {
                SegmentedPair(first = "Everything", second = "Who did what", selected = 0, onSelect = {})
            }
        }
        ActivityList(flat.events, flat.members, ME)
    }
}
