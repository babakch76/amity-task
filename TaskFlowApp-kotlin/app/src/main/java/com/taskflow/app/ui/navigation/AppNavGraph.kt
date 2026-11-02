package com.taskflow.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.taskflow.app.data.local.KnownHouseholds
import com.taskflow.app.data.local.LastHousehold
import com.taskflow.app.data.local.TokenManager
import com.taskflow.app.reminders.HouseNewsWorker
import com.taskflow.app.ui.screens.MainShell
import com.taskflow.app.ui.screens.LoginScreen
import com.taskflow.app.ui.screens.RegisterScreen

/**
 * Route constants for type-safe navigation.
 */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"

    /**
     * The three-tab shell: My groups, My chores, Settings.
     *
     * One outer route for all three, and — since the board became the first
     * tab rather than a screen pushed on top of a group list — the only route
     * a signed-in person is ever on. The tabs have their own nested graph
     * inside [MainShell], so switching between them never touches this one and
     * each keeps its own state.
     */
    const val SHELL = "shell"
}

/**
 * Root navigation graph for the app.
 *
 * Checks [TokenManager.isLoggedIn] to determine the start destination:
 *  - If a token exists → jump straight to the shell
 *  - If not → show Login
 *
 * Login and Register toggle between each other with a text button; both land on
 * the shell on success.
 *
 * Three routes, and that is the whole graph: the board is the shell's first
 * tab, and the create-chore flow that used to need a route of its own is a
 * full-screen `Dialog` drawn over everything, bottom bar included.
 *
 * @param tokenManager The process-wide instance owned by TaskFlowApp, passed
 *   down from MainActivity. This graph must not construct its own — see
 *   TaskFlowApp for why.
 */
@Composable
fun AppNavGraph(
    tokenManager: TokenManager,
    navController: NavHostController = rememberNavController(),
) {
    // Read once, when the graph is first composed: the start destination can't
    // change under a live NavHost anyway.
    val startDestination = if (tokenManager.isLoggedIn()) {
        Routes.SHELL
    } else {
        Routes.LOGIN
    }

    /** Shared by Login and Register — both fully replace the auth back stack. */
    fun goToShell() {
        navController.navigate(Routes.SHELL) {
            // popUpTo(0) clears login AND register, so back from the shell
            // never returns to an auth screen.
            popUpTo(0) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        // ─── Login ───
        composable(Routes.LOGIN) {
            LoginScreen(
                tokenManager = tokenManager,
                onLoginSuccess = { goToShell() },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER) {
                        // Only ever one auth screen on the stack.
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        // ─── Register ───
        composable(Routes.REGISTER) {
            // Only one auth screen is ever on the stack, so system Back here
            // would leave the app. It goes back to Sign in instead, which is
            // where someone pressing Back on "Create account" expects to be.
            androidx.activity.compose.BackHandler {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.REGISTER) { inclusive = true }
                }
            }
            RegisterScreen(
                tokenManager = tokenManager,
                onRegisterSuccess = { goToShell() },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
            )
        }

        // ─── Shell (My groups · My chores · Settings) ───
        composable(Routes.SHELL) {
            val context = LocalContext.current
            MainShell(
                onLogout = {
                    tokenManager.clearToken()
                    // Which household this phone opens on belonged to the
                    // account that just left. Forgetting it here means the next
                    // person does not open on it, even for the moment it would
                    // take to be resolved away.
                    LastHousehold.clear(context)
                    KnownHouseholds.clear(context)
                    HouseNewsWorker.stop(context)
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
