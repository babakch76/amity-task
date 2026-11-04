package com.taskflow.app.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.taskflow.app.data.local.TokenManager
import com.taskflow.app.data.model.LoginRequest
import com.taskflow.app.data.remote.ApiErrors
import com.taskflow.app.data.remote.RetrofitClient
import com.taskflow.app.data.remote.SessionManager
import com.taskflow.app.ui.components.AuthErrorLine
import com.taskflow.app.ui.components.AuthField
import com.taskflow.app.ui.components.AuthHeader
import com.taskflow.app.ui.components.AuthSubmitButton
import com.taskflow.app.ui.components.NoticeCallout
import com.taskflow.app.ui.components.mutedInk
import com.taskflow.app.ui.theme.TaskFlowTheme
import kotlinx.coroutines.launch

/**
 * Everything the login screen draws, and nothing about how it is fetched.
 *
 * Split out of [LoginScreen] so the layout can be rendered by `@Preview`
 * without a [TokenManager] (which needs the Android Keystore) or a live
 * backend. Every state the screen can be in is a value of this class, so a
 * preview is one constructor call — see the previews at the bottom of the file.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    /** Why the last attempt failed. */
    val errorMessage: String? = null,
    /** Why the person is looking at this screen at all, e.g. an expired session. */
    val noticeMessage: String? = null,
    /** Shown for the instant between a successful call and the navigation. */
    val successMessage: String? = null,
)

/**
 * Login screen for the auth flow.
 *
 * Calls POST /auth/login via Retrofit, saves the JWT via [TokenManager],
 * and reports success/failure. This is enough to verify:
 *  1. Retrofit reaches the Go backend at BuildConfig.BASE_URL
 *  2. Gson correctly parses the AuthResponse (including OffsetDateTime)
 *  3. The JWT is stored in EncryptedSharedPreferences
 *  4. Subsequent requests include the Authorization header
 *
 * This composable owns the state and the network call; the layout lives in
 * [LoginContent], which is stateless and previewable.
 *
 * @param tokenManager The process-wide instance from TaskFlowApp. Passed in
 *   rather than constructed here: building EncryptedSharedPreferences inside a
 *   composition puts a Keystore round-trip on the UI thread.
 * @param onLoginSuccess Called when the login succeeds (navigate to home).
 * @param onNavigateToRegister Called when the user taps "Create one".
 */
@Composable
fun LoginScreen(
    tokenManager: TokenManager,
    onLoginSuccess: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    // If a 401 bounced the user here mid-task, say so. Read once on first
    // composition: consuming it means a later manual sign-out stays silent.
    var state by remember {
        mutableStateOf(
            LoginUiState(
                noticeMessage = if (SessionManager.consumeExpiryNotice()) {
                    "Your session expired. Please sign in again."
                } else null
            )
        )
    }

    fun doLogin() {
        if (state.email.isBlank() || state.password.isBlank()) {
            state = state.copy(errorMessage = "Email and password are required")
            return
        }

        state = state.copy(isLoading = true, errorMessage = null, successMessage = null)

        scope.launch {
            try {
                val response = RetrofitClient.getInstance()
                    .authApi
                    .login(LoginRequest(email = state.email.trim(), password = state.password))

                val body = response.body()
                if (response.isSuccessful && body != null) {
                    tokenManager.saveSession(body.token, body.user.id)
                    state = state.copy(successMessage = "Welcome, ${body.user.username}!")
                    onLoginSuccess()
                } else if (response.isSuccessful) {
                    // 2xx with no parseable body — the server answered, but not
                    // with the token we need, so there is nothing to log in with.
                    state = state.copy(
                        errorMessage = ApiErrors.SOMETHING_WRONG
                    )
                } else {
                    state = state.copy(errorMessage = ApiErrors.messageFor(response))
                }
            } catch (e: Exception) {
                state = state.copy(
                    errorMessage = ApiErrors.forFailure(e)
                )
            } finally {
                state = state.copy(isLoading = false)
            }
        }
    }

    LoginContent(
        state = state,
        // Typing clears the last failure: the message described an attempt the
        // user has now changed.
        onEmailChange = { state = state.copy(email = it, errorMessage = null, noticeMessage = null) },
        onPasswordChange = { state = state.copy(password = it, errorMessage = null) },
        onSubmit = ::doLogin,
        onNavigateToRegister = onNavigateToRegister,
    )
}

/**
 * The login layout. Stateless, so it renders identically in a `@Preview` and in
 * the app — work on the design here, not in [LoginScreen].
 *
 * The header, the fields and the pill are shared with [RegisterScreen] and live
 * in `ui/components/AuthScaffold.kt`. The mock's "Forgot?" link is deliberately
 * not here — there is no password-reset route on the backend, so it would be a
 * dead link.
 */
@Composable
fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    // Whether the password is revealed is a property of this phone at this
    // moment, not of the sign-in attempt, so it stays out of [LoginUiState].
    // rememberSaveable: a rotation mid-typing should not re-hide it.
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // imePadding + scroll: without these the soft keyboard covers
                // the submit button with no way to reach it.
                .imePadding()
                // Edge-to-edge: keep the form off the status and gesture bars
                // when it is taller than the screen and scrolls to the top.
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthHeader()

            // The expired-session notice sits above the fields: it explains why
            // this screen is here at all, so it has to be read before they are
            // filled in.
            AnimatedVisibility(visible = state.noticeMessage != null) {
                Column {
                    Spacer(modifier = Modifier.height(24.dp))
                    NoticeCallout(state.noticeMessage ?: "")
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ─── Email field ───
            AuthField(
                value = state.email,
                onValueChange = onEmailChange,
                label = "Email",
                placeholder = "e.g. maya@example.com",
                leadingIcon = Icons.Outlined.Email,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Password field ───
            AuthField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = "Password",
                placeholder = "Enter your password",
                leadingIcon = Icons.Outlined.Lock,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !state.isLoading,
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password"
                            else "Show password",
                            tint = mutedInk(),
                        )
                    }
                },
            )

            // ─── Failure, as an inline line under the fields ───
            AnimatedVisibility(visible = state.errorMessage != null) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    AuthErrorLine(state.errorMessage ?: "")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AuthSubmitButton(
                label = "Sign in",
                onClick = onSubmit,
                isLoading = state.isLoading,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ─── Toggle to Register ───
            TextButton(
                onClick = onNavigateToRegister,
                enabled = !state.isLoading,
            ) {
                Text(
                    text = "New here? Create an account.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            // Shown for the instant between a successful call and the
            // navigation away, so it stays quiet and out of the way.
            AnimatedVisibility(visible = state.successMessage != null) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.successMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
//
// These are the design surface for this screen: edit [LoginContent], watch the
// panel. They are interactive — start interactive mode in the preview toolbar
// and the fields type, because each preview holds its own state.
//
// [TaskFlowTheme] is applied with an explicit `darkTheme`, not the system one,
// so the light and dark previews stay what they say they are.
// ─────────────────────────────────────────────────────────────────────────────

/** Drives a preview: holds state locally so typing works in interactive mode. */
@Composable
private fun LoginPreview(
    dark: Boolean = false,
    initial: LoginUiState = LoginUiState(),
) {
    var state by remember { mutableStateOf(initial) }
    TaskFlowTheme(darkTheme = dark) {
        LoginContent(
            state = state,
            onEmailChange = { state = state.copy(email = it, errorMessage = null, noticeMessage = null) },
            onPasswordChange = { state = state.copy(password = it, errorMessage = null) },
            onSubmit = { state = state.copy(isLoading = !state.isLoading) },
            onNavigateToRegister = {},
        )
    }
}

@Preview(name = "1 · Empty", showBackground = true, showSystemUi = true)
@Composable
private fun LoginEmptyPreview() = LoginPreview()

@Preview(name = "2 · Filled", showBackground = true, showSystemUi = true)
@Composable
private fun LoginFilledPreview() = LoginPreview(
    initial = LoginUiState(email = "maya@flat3b.example", password = "correcthorse"),
)

@Preview(name = "3 · Signing in", showBackground = true, showSystemUi = true)
@Composable
private fun LoginLoadingPreview() = LoginPreview(
    initial = LoginUiState(
        email = "maya@flat3b.example",
        password = "correcthorse",
        isLoading = true,
    ),
)

@Preview(name = "4 · Wrong password", showBackground = true, showSystemUi = true)
@Composable
private fun LoginErrorPreview() = LoginPreview(
    initial = LoginUiState(
        email = "maya@flat3b.example",
        errorMessage = "Email or password is wrong.",
    ),
)

@Preview(name = "5 · Session expired", showBackground = true, showSystemUi = true)
@Composable
private fun LoginNoticePreview() = LoginPreview(
    initial = LoginUiState(
        noticeMessage = "Your session expired. Please sign in again.",
    ),
)

/** The longest message the screen can get: an unreachable server. */
@Preview(name = "6 · Network error (long)", showBackground = true, showSystemUi = true)
@Composable
private fun LoginLongErrorPreview() = LoginPreview(
    initial = LoginUiState(
        email = "maya@flat3b.example",
        errorMessage = "Can't reach the server. Check your connection and try again.",
    ),
)

@Preview(
    name = "7 · Dark",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun LoginDarkPreview() = LoginPreview(
    dark = true,
    initial = LoginUiState(email = "maya@flat3b.example", password = "correcthorse"),
)

/** Accessibility: 200% text. Nothing may clip or overlap. */
@Preview(name = "8 · Font scale 2.0", showBackground = true, showSystemUi = true, fontScale = 2.0f)
@Composable
private fun LoginLargeTextPreview() = LoginPreview(
    initial = LoginUiState(
        email = "maya@flat3b.example",
        errorMessage = "Email or password is wrong.",
    ),
)

/** The smallest screen the app supports at minSdk 26. */
@Preview(
    name = "9 · Small screen",
    showBackground = true,
    device = "spec:width=320dp,height=568dp,dpi=320",
)
@Composable
private fun LoginSmallScreenPreview() = LoginPreview(
    initial = LoginUiState(email = "maya@flat3b.example", password = "correcthorse"),
)
