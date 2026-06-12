package com.github.woodsmarshes.chat.feature.auth.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.AppTheme
import com.github.woodsmarshes.chat.feature.auth.model.AuthMode
import com.github.woodsmarshes.chat.feature.auth.model.AuthScreenState
import com.github.woodsmarshes.chat.feature.auth.model.AuthUiState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = koinViewModel(),
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass,
    onAuthSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.screenState) {
        when (val state = uiState.screenState) {
            is AuthScreenState.Success -> onAuthSuccess()
            is AuthScreenState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetScreenState()
            }
            else -> {}
        }
    }

    AuthScreenContent(
        uiState = uiState,
        windowSizeClass = windowSizeClass,
        onModeChange = viewModel::setMode,
        onNameChange = viewModel::updateName,
        onEmailChange = viewModel::updateEmail,
        onPasswordChange = viewModel::updatePassword,
        onConfirmPasswordChange = viewModel::updateConfirmPassword,
        onSubmit = viewModel::submit,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AuthScreenContent(
    uiState: AuthUiState,
    windowSizeClass: WindowSizeClass,
    onModeChange: (AuthMode) -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
        // 使用 androidx.window.core.layout.WindowSizeClass 提供的断点判断方法
        // WIDTH_DP_MEDIUM_LOWER_BOUND = 600
        val isDesktopOrTablet = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isDesktopOrTablet) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.8f).height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AuthBrandingSection()
                    }
                    Card(
                        modifier = Modifier.weight(1.2f),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        AuthFormContent(
                            uiState = uiState,
                            onModeChange = onModeChange,
                            onNameChange = onNameChange,
                            onEmailChange = onEmailChange,
                            onPasswordChange = onPasswordChange,
                            onConfirmPasswordChange = onConfirmPasswordChange,
                            onSubmit = onSubmit
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AuthBrandingSection()
                    Spacer(modifier = Modifier.height(32.dp))
                    AuthFormContent(
                        uiState = uiState,
                        onModeChange = onModeChange,
                        onNameChange = onNameChange,
                        onEmailChange = onEmailChange,
                        onPasswordChange = onPasswordChange,
                        onConfirmPasswordChange = onConfirmPasswordChange,
                        onSubmit = onSubmit
                    )
                }
            }
        }

}

@Composable
private fun AuthBrandingSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = LocalStrings.current.appName,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = LocalStrings.current.appTagline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun AuthFormContent(
    uiState: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (uiState.mode == AuthMode.Login) LocalStrings.current.authWelcomeBack else LocalStrings.current.createAccount,
            style = MaterialTheme.typography.headlineSmall
        )

        SecondaryTabRow(
            selectedTabIndex = if (uiState.mode == AuthMode.Login) 0 else 1,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            Tab(
                selected = uiState.mode == AuthMode.Login,
                onClick = { onModeChange(AuthMode.Login) },
                text = { Text(LocalStrings.current.login) }
            )
            Tab(
                selected = uiState.mode == AuthMode.Register,
                onClick = { onModeChange(AuthMode.Register) },
                text = { Text(LocalStrings.current.register) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedVisibility(
            visible = uiState.mode == AuthMode.Register,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text(LocalStrings.current.nameLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.nameError != null,
                supportingText = uiState.nameError?.let { { Text(it) } },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
        }

        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = { Text(LocalStrings.current.emailLabel) },
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.emailError != null,
            supportingText = uiState.emailError?.let { { Text(it) } },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )

        var passwordVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text(LocalStrings.current.passwordLabel) },
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.passwordError != null,
            supportingText = uiState.passwordError?.let { { Text(it) } },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (uiState.mode == AuthMode.Login) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { if (uiState.canSubmit) onSubmit() }
            )
        )

        AnimatedVisibility(
            visible = uiState.mode == AuthMode.Register,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text(LocalStrings.current.confirmPasswordLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.confirmPasswordError != null,
                supportingText = uiState.confirmPasswordError?.let { { Text(it) } },
                leadingIcon = { Icon(Icons.Default.CheckCircle, null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (uiState.canSubmit) onSubmit() })
            )
        }

        Button(
            onClick = {
                if (uiState.screenState !is AuthScreenState.Loading) {
                    onSubmit()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = uiState.canSubmit,
            shape = MaterialTheme.shapes.medium
        ) {
            if (uiState.screenState is AuthScreenState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    if (uiState.mode == AuthMode.Login) {
                        LocalStrings.current.login
                    } else {
                        LocalStrings.current.createAccount
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun AuthScreenLoginPreview() {
    AppTheme {
        AuthScreenContent(
            uiState = AuthUiState(
                mode = AuthMode.Login,
                email = "test@example.com"
            ),
            windowSizeClass = WindowSizeClass(400, 800),
            onModeChange = {},
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onSubmit = {}
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun AuthScreenRegisterPreview() {
    AppTheme {
        AuthScreenContent(
            uiState = AuthUiState(
                mode = AuthMode.Register,
                name = "Test User",
                email = "test@example.com"
            ),
            windowSizeClass = WindowSizeClass(1000, 800),
            onModeChange = {},
            onNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onSubmit = {}
        )
    }
}
