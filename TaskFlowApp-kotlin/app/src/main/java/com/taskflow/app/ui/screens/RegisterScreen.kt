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
import androidx.compose.material.icons.outlined.Person
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
import com.taskflow.app.data.model.RegisterRequest
import com.taskflow.app.data.remote.ApiErrors
import com.taskflow.app.data.remote.RetrofitClient
import com.taskflow.app.ui.components.AuthErrorLine
import com.taskflow.app.ui.components.AuthField
import com.taskflow.app.ui.components.AuthHeader
import com.taskflow.app.ui.components.AuthSubmitButton
import com.taskflow.app.ui.components.mutedInk
import com.taskflow.app.ui.theme.TaskFlowTheme
import kotlinx.coroutines.launch

/** Backend rule in handlers/auth.go: `len(req.Password) < 8` is rejected. */
private const val MIN_PASSWORD_LENGTH = 8

/**
 * Everything the register screen draws, and nothing about how it is sent.
 *
 * The mirror of [LoginUiState] — see the note there on why the layout takes a
 * value class rather than reaching for the network itself.
 */
data class RegisterUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    /** Why the last attempt failed. */
    val errorMessage: String? = null,
    /** Shown for the instant between a successful call and the navigation. */
    val successMessage: String? = null,
)

/**
 * Account creation screen — the mirror of [LoginScreen].
 *
 * Calls POST /auth/register, which returns the same `{ token, user }` payload as
 * login, so a successful registration logs the user straight in: the JWT is
 * saved and the caller navigates on.
 *
 * Client-side validation matches the backend's rules exactly (username, email
 * and password all required; password at least [MIN_PASSWORD_LENGTH]
 * characters) so the common mistakes are caught without a round trip. The
 * server remains the authority — anything it rejects is surfaced through
 * [ApiErrors].
 *
 * @param tokenManager The process-wide instance from TaskFlowApp.
 * @param onRegisterSuccess Called when registration succeeds (navigate to home).
 * @param onNavigateToLogin Called when the user taps "Sign in".
 */
@Composable
fun RegisterScreen(
    tokenManager: TokenManager,
    onRegisterSuccess: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(RegisterUiState()) }

    fun doRegister() {
        if (state.username.isBlank() || state.email.isBlank() || state.password.isBlank()) {
            state = state.copy(errorMessage = "Username, email and password are required")
            return
        }
        if (state.password.length < MIN_PASSWORD_LENGTH) {
            state = state.copy(
                errorMessage = "Password must be at least $MIN_PASSWORD_LENGTH characters"
            )
            return
        }

        state = state.copy(isLoading = true, errorMessage = null, successMessage = null)

        scope.launch {
            try {
                val response = RetrofitClient.getInstance()
                    .authApi
                    .register(
                        RegisterRequest(
                            username = state.username.trim(),
                            email = state.email.trim(),
                            password = state.password,
                        )
                    )

                val body = response.body()
                if (response.isSuccessful && body != null) {
                    // /auth/register returns a token too — no second login needed.
                    tokenManager.saveSession(body.token, body.user.id)
                    state = state.copy(
                        successMessage = "Account created. Welcome, ${body.user.username}!"
                    )
                    onRegisterSuccess()
                } else if (response.isSuccessful) {
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

    RegisterContent(
        state = state,
        // Typing clears the last failure: the message described an attempt the
        // user has now changed.
        onUsernameChange = { state = state.copy(username = it, errorMessage = null) },
        onEmailChange = { state = state.copy(email = it, errorMessage = null) },
        onPasswordChange = { state = state.copy(password = it, errorMessage = null) },
        onSubmit = ::doRegister,
        onNavigateToLogin = onNavigateToLogin,
    )
}

/**
 * The register layout. Stateless, so it renders identically in a `@Preview` and
 * in the app.
 *
 * The header, the fields and the pill are the same pieces sign-in uses, from
 * `ui/components/AuthScaffold.kt`: only the fields on them and the action
 * differ. There is no strength meter and no step indicator — DESIGN.md §5.2.
 */
@Composable
fun RegisterContent(
    state: RegisterUiState,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
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
            AuthHeader(title = "Join your\nhouse", subtitle = "Make an account, then start a household or join one with a code.")

            Spacer(modifier = Modifier.height(40.dp))

            // ─── Username field ───
            AuthField(
                value = state.username,
                onValueChange = onUsernameChange,
                label = "Username",
                placeholder = "e.g. Alex",
                leadingIcon = Icons.Outlined.Person,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                helper = "This is the name your housemates will see.",
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Email field ───
            AuthField(
                value = state.email,
                onValueChange = onEmailChange,
                label = "Email",
                placeholder = "alex@example.com",
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
                placeholder = "Create a password",
                leadingIcon = Icons.Outlined.Lock,
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                // The rule, stated up front rather than as a failure after the
                // fact. It is the backend's rule, not a second opinion.
                helper = "At least $MIN_PASSWORD_LENGTH characters.",
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
                label = "Create account",
                onClick = onSubmit,
                isLoading = state.isLoading,
            )

            // Shown for the instant between a successful call and the
            // navigation away, so it stays quiet and out of the way.
            AnimatedVisibility(visible = state.successMessage != null) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.successMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Toggle to Login ───
            // Muted rather than violet: on this screen the way back is the
            // quieter of the two things you can do.
            TextButton(
                onClick = onNavigateToLogin,
                enabled = !state.isLoading,
            ) {
                Text(
                    text = "Already have an account? Sign in.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = mutedInk(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
//
// As on the login screen: edit [RegisterContent], watch the panel. Interactive
// mode types, because each preview holds its own state.
// ─────────────────────────────────────────────────────────────────────────────

/** Drives a preview: holds state locally so typing works in interactive mode. */
@Composable
private fun RegisterPreview(
    dark: Boolean = false,
    initial: RegisterUiState = RegisterUiState(),
) {
    var state by remember { mutableStateOf(initial) }
    TaskFlowTheme(darkTheme = dark) {
        RegisterContent(
            state = state,
            onUsernameChange = { state = state.copy(username = it, errorMessage = null) },
            onEmailChange = { state = state.copy(email = it, errorMessage = null) },
            onPasswordChange = { state = state.copy(password = it, errorMessage = null) },
            onSubmit = { state = state.copy(isLoading = !state.isLoading) },
            onNavigateToLogin = {},
        )
    }
}

@Preview(name = "1 · Empty", showBackground = true, showSystemUi = true)
@Composable
private fun RegisterEmptyPreview() = RegisterPreview()

@Preview(name = "2 · Filled", showBackground = true, showSystemUi = true)
@Composable
private fun RegisterFilledPreview() = RegisterPreview(
    initial = RegisterUiState(
        username = "maya",
        email = "maya@flat3b.example",
        password = "correcthorse",
    ),
)

@Preview(name = "3 · Creating", showBackground = true, showSystemUi = true)
@Composable
private fun RegisterLoadingPreview() = RegisterPreview(
    initial = RegisterUiState(
        username = "maya",
        email = "maya@flat3b.example",
        password = "correcthorse",
        isLoading = true,
    ),
)

@Preview(name = "4 · Email taken", showBackground = true, showSystemUi = true)
@Composable
private fun RegisterErrorPreview() = RegisterPreview(
    initial = RegisterUiState(
        username = "maya",
        email = "maya@flat3b.example",
        errorMessage = "That email is already registered.",
    ),
)

@Preview(
    name = "5 · Dark",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun RegisterDarkPreview() = RegisterPreview(
    dark = true,
    initial = RegisterUiState(
        username = "maya",
        email = "maya@flat3b.example",
        password = "correcthorse",
    ),
)

/** Accessibility: 200% text. Nothing may clip or overlap. */
@Preview(name = "6 · Font scale 2.0", showBackground = true, showSystemUi = true, fontScale = 2.0f)
@Composable
private fun RegisterLargeTextPreview() = RegisterPreview(
    initial = RegisterUiState(
        username = "maya",
        email = "maya@flat3b.example",
        errorMessage = "Password must be at least 8 characters",
    ),
)

/** The smallest screen the app supports at minSdk 26. */
@Preview(
    name = "7 · Small screen",
    showBackground = true,
    device = "spec:width=320dp,height=568dp,dpi=320",
)
@Composable
private fun RegisterSmallScreenPreview() = RegisterPreview(
    initial = RegisterUiState(
        username = "maya",
        email = "maya@flat3b.example",
        password = "correcthorse",
    ),
)
