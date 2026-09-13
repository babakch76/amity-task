package com.taskflow.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.taskflow.app.TaskFlowApp
import com.taskflow.app.data.local.LastHousehold
import com.taskflow.app.reminders.ReminderScheduler

/**
 * The three places the app keeps for a signed-in person, and the bar that
 * switches between them.
 *
 * The split is the point, and it is the one from DESIGN.md §4.1: **My
 * household** is the household's, **My chores** is yours across every
 * household, and **Settings** is yours alone. Two of the three are private,
 * and the bar says so by keeping them apart rather than folding personal
 * settings into an overflow menu on a shared screen — which is where they used
 * to live, and which said the opposite.
 *
 * The first tab is the **board itself**, not a list of households: the app
 * opens on the one you last had open, and the switcher in its app bar is how
 * you change it. §4.2 deletes the list as a destination, and this is that.
 *
 * ### Which household is open
 *
 * [currentGroupId] is held here rather than inside the tab, because the tab it
 * belongs to is rebuilt whenever the bottom bar restores it, and because it is
 * persisted to the device by [LastHousehold] so the next cold start opens in
 * the same place.
 *
 * ### One ViewModel for all three tabs
 *
 * [ShellViewModel] is created here, at the shell, and handed down. It
 * already fetches the group list, the invites, the account row, and every
 * household's occurrences — which is exactly the union of what the three tabs
 * need. Giving each tab its own instance would run that set of requests three
 * times over and let the tabs disagree about what the answer was.
 *
 * The shell also owns the three things that must keep happening whichever tab
 * is in front: the reminder alarms, the invite poll, and the snackbar.
 */
@Composable
fun MainShell(
    onLogout: () -> Unit,
    viewModel: ShellViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    val invites by viewModel.invites.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Arm reminders for every group the user is in (F3, B-8).
    //
    // The board can only ever schedule for the group it is showing, so a
    // second household's turns went unmentioned until you opened it. This runs
    // at the shell rather than on the group list because it is not that list's
    // business, and because it must keep happening while you are reading any
    // of the three tabs.
    val reminderData by viewModel.reminderData.collectAsState()
    val quietHours by viewModel.quietHours.collectAsState()
    val context = LocalContext.current
    val myUserId = remember {
        (context.applicationContext as? TaskFlowApp)?.tokenManager?.getUserId()
    }

    // Seeded from the device, then corrected by HouseholdTab against the group
    // list it actually has — the stored id may name a household you have left,
    // or one belonging to whoever was signed in before.
    var currentGroupId by rememberSaveable { mutableStateOf(LastHousehold.get(context)) }

    /** Open a household, and remember it for the next cold start. */
    fun selectGroup(groupId: String) {
        currentGroupId = groupId
        LastHousehold.set(context, groupId)
    }

    LaunchedEffect(reminderData, quietHours, myUserId) {
        reminderData.forEach { group ->
            ReminderScheduler.reschedule(
                context = context.applicationContext,
                groupId = group.groupId,
                occurrences = group.occurrences,
                chores = group.chores,
                myUserId = myUserId,
                quiet = quietHours,
            )
        }
    }

    // An invite that arrives while the app is open should appear on its own.
    // A slower cadence than the activity feed on purpose — invites are rare,
    // and this also refreshes immediately whenever the app is resumed.
    PollWhileResumed(intervalMillis = INVITE_POLL_MILLIS) {
        viewModel.fetchInvites()
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                ShellDestination.entries.forEach { destination ->
                    val selected = currentRoute?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) return@NavigationBarItem
                            navController.navigate(destination.route) {
                                // Keep one tab on the stack at a time, and put
                                // each one back the way it was left — its
                                // scroll position included.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val icon = if (selected) destination.selectedIcon else destination.icon
                            // The one badge in the bar, and deliberately the
                            // only one: invitations are addressed to you and
                            // sit in Settings, so the bar has to say something.
                            // No counts anywhere else — a number in a
                            // navigation bar is a thing to compare.
                            if (destination == ShellDestination.SETTINGS && invites.isNotEmpty()) {
                                BadgedBox(badge = { Badge() }) {
                                    Icon(icon, contentDescription = null)
                                }
                            } else {
                                Icon(icon, contentDescription = null)
                            }
                        },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(
                navController = navController,
                startDestination = ShellDestination.HOUSEHOLD.route,
            ) {
                composable(ShellDestination.HOUSEHOLD.route) {
                    HouseholdTab(
                        currentGroupId = currentGroupId,
                        onSelectGroup = ::selectGroup,
                        viewModel = viewModel,
                    )
                }
                composable(ShellDestination.MY_CHORES.route) {
                    // Tapping a turn here opens the board's own detail sheet in
                    // place rather than switching the board's household: the
                    // sheet carries everything that used to need the trip.
                    MyChoresScreen(
                        viewModel = viewModel,
                        myUserId = myUserId,
                    )
                }
                composable(ShellDestination.SETTINGS.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onLogout = onLogout,
                    )
                }
            }
        }
    }
}

/**
 * The three tabs, in the order they appear.
 *
 * An enum rather than three route constants, because the bar has to iterate
 * them and the icons belong next to the routes they label.
 */
private enum class ShellDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOUSEHOLD(
        route = "household",
        label = "My groups",
        icon = Icons.Outlined.Groups,
        selectedIcon = Icons.Filled.Groups,
    ),
    MY_CHORES(
        route = "mychores",
        label = "My chores",
        icon = Icons.Outlined.Checklist,
        selectedIcon = Icons.Filled.Checklist,
    ),
    SETTINGS(
        route = "settings",
        label = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
    ),
}
