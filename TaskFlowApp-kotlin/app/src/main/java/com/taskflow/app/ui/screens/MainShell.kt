package com.taskflow.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.taskflow.app.TaskFlowApp
import com.taskflow.app.data.local.LastHousehold
import com.taskflow.app.reminders.ReminderScheduler
import com.taskflow.app.ui.components.DockTab
import com.taskflow.app.ui.components.FloatingDock
import com.taskflow.app.ui.components.QuietSnackbar

/**
 * The signed-in app: three places behind a floating dock — **Mine** (your
 * turns, across every household), **House** (the open household's board) and
 * **Feed** (what has happened there) — plus Add, which opens the new-chore
 * flow on the open household. **People** opens from the faces on House, and
 * **You** (settings) from your face on Mine; both are pages without the dock,
 * with a way back.
 *
 * ### Which household is open
 *
 * [currentGroupId] is held here, persisted by [LastHousehold] so the next cold
 * start opens in the same place, and shared by House, Feed and People.
 *
 * ### One ViewModel each, created here
 *
 * [ShellViewModel] is created at the shell and handed down, as before. The
 * open household's [GroupDetailViewModel] is now created against the shell's
 * store too ([shellOwner]), so House, Feed and People — three destinations —
 * read one board rather than loading it three times.
 *
 * The shell also owns what must keep happening whichever place is in front:
 * the reminder alarms, the invite poll, and the toast.
 */
@Composable
fun MainShell(
    onLogout: () -> Unit,
    viewModel: ShellViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val shellOwner = checkNotNull(LocalViewModelStoreOwner.current)

    val invites by viewModel.invites.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Add on the dock opens the create flow on House. It is a request rather
    // than a navigation argument so the flow opens once, not on every return.
    var createRequested by remember { mutableStateOf(false) }

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

    PollWhileResumed(intervalMillis = INVITE_POLL_MILLIS) {
        viewModel.fetchInvites()
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            val undo = viewModel.undoableDone.value
            val undoPass = viewModel.undoablePass.value
            val result = snackbarHostState.showSnackbar(
                message = it,
                actionLabel = (undo ?: undoPass)?.let { "Undo" },
                duration = SnackbarDuration.Short,
            )
            viewModel.clearActionMessage()
            if (result == SnackbarResult.ActionPerformed) {
                when {
                    undoPass != null -> viewModel.undoPass(undoPass.first, undoPass.second)
                    undo != null -> viewModel.setOccurrenceDone(undo.first, undo.second, done = false)
                }
            }
        }
    }

    fun go(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            // One place on the stack at a time, each restored as it was left.
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val dockTab = when (currentRoute) {
        ROUTE_MINE -> DockTab.MINE
        ROUTE_HOUSE -> DockTab.HOUSE
        ROUTE_FEED -> DockTab.FEED
        else -> null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = if (dockTab != null) 96.dp else 0.dp),
            ) { QuietSnackbar(it) }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Only the top: content scrolls down under the dock and the
                // gesture bar, and each list clears them with DOCK_CLEARANCE.
                .padding(top = padding.calculateTopPadding()),
        ) {
            NavHost(navController = navController, startDestination = ROUTE_MINE) {
                composable(ROUTE_MINE) {
                    MyChoresScreen(
                        viewModel = viewModel,
                        myUserId = myUserId,
                        hasInvites = invites.isNotEmpty(),
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    )
                }
                composable(ROUTE_HOUSE) {
                    HouseholdTab(
                        section = HouseSection.BOARD,
                        currentGroupId = currentGroupId,
                        onSelectGroup = ::selectGroup,
                        viewModel = viewModel,
                        vmOwner = shellOwner,
                        createRequested = createRequested,
                        onCreateHandled = { createRequested = false },
                        onOpenPeople = { navController.navigate(ROUTE_PEOPLE) },
                        onBack = {},
                    )
                }
                composable(ROUTE_FEED) {
                    HouseholdTab(
                        section = HouseSection.FEED,
                        currentGroupId = currentGroupId,
                        onSelectGroup = ::selectGroup,
                        viewModel = viewModel,
                        vmOwner = shellOwner,
                        createRequested = false,
                        onCreateHandled = {},
                        onOpenPeople = { navController.navigate(ROUTE_PEOPLE) },
                        onBack = {},
                    )
                }
                composable(ROUTE_PEOPLE) {
                    HouseholdTab(
                        section = HouseSection.PEOPLE,
                        currentGroupId = currentGroupId,
                        onSelectGroup = ::selectGroup,
                        viewModel = viewModel,
                        vmOwner = shellOwner,
                        createRequested = false,
                        onCreateHandled = {},
                        onOpenPeople = {},
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onLogout = onLogout,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            if (dockTab != null) {
                FloatingDock(
                    current = dockTab,
                    onSelect = { tab ->
                        go(
                            when (tab) {
                                DockTab.MINE -> ROUTE_MINE
                                DockTab.HOUSE -> ROUTE_HOUSE
                                DockTab.FEED -> ROUTE_FEED
                            },
                        )
                    },
                    onAdd = {
                        createRequested = true
                        go(ROUTE_HOUSE)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private const val ROUTE_MINE = "mine"
private const val ROUTE_HOUSE = "house"
private const val ROUTE_FEED = "feed"
private const val ROUTE_PEOPLE = "people"
private const val ROUTE_SETTINGS = "settings"
