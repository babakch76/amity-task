package com.taskflow.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.taskflow.app.data.local.TokenManager
import com.taskflow.app.ui.screens.GroupDetailScreen
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
     * One outer route for all three. The tabs have their own nested graph
     * inside [MainShell], so switching between them never touches this one and
     * each keeps its own state.
     */
    const val SHELL = "shell"
    const val GROUP_DETAIL = "group/{groupId}"

    fun groupDetail(groupId: String) = "group/$groupId"
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
 * The board sits outside the shell on its own route, so it fills the screen
 * with no bottom bar under it — DESIGN.md §4.1 keeps the bar off the board's
 * sub-flows, and the create-chore flow it leads to needs the whole screen.
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
            MainShell(
                onGroupClick = { groupId ->
                    navController.navigate(Routes.groupDetail(groupId))
                },
                onLogout = {
                    tokenManager.clearToken()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // ─── Group Detail ───
        composable(
            route = Routes.GROUP_DETAIL,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType }
            ),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            GroupDetailScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
