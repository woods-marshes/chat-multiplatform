package com.github.woodsmarshes.chat.feature.auth.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.ArcMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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

    SharedTransitionLayout {
        AuthScreenContent(
            uiState = uiState,
            windowSizeClass = windowSizeClass,
            onModeChange = viewModel::setMode,
            onNameChange = viewModel::updateName,
            onEmailChange = viewModel::updateEmail,
            onPasswordChange = viewModel::updatePassword,
            onConfirmPasswordChange = viewModel::updateConfirmPassword,
            onSubmit = viewModel::submit,
            snackbarHostState = snackbarHostState,
            sharedTransitionScope = this
        )
    }
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
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    sharedTransitionScope: SharedTransitionScope
) {
    // 使用 androidx.window.core.layout.WindowSizeClass 提供的断点判断方法
    // WIDTH_DP_MEDIUM_LOWER_BOUND = 600
    val isDesktopOrTablet = windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val brandingBoundsTransform = BoundsTransform { initialBounds, targetBounds ->
        keyframes {
            durationMillis = 600
            initialBounds at 0 using ArcMode.ArcAbove using FastOutSlowInEasing
            targetBounds at 600
        }
    }

    val formBoundsTransform = BoundsTransform { initialBounds, targetBounds ->
        keyframes {
            durationMillis = 600
            initialBounds at 0 using ArcMode.ArcBelow using FastOutSlowInEasing
            targetBounds at 600
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isDesktopOrTablet,
            label = "Auth-LayoutTransition",
            transitionSpec = {
                fadeIn(animationSpec = tween(500)) togetherWith
                        fadeOut(animationSpec = tween(500))
            }
        ) { targetIsDesktopOrTablet ->

            if (targetIsDesktopOrTablet) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(0.8f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(48.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            with(sharedTransitionScope) {
                                AuthBrandingSection(
                                    modifier =  Modifier
                                        .sharedBounds(
                                            sharedContentState = rememberSharedContentState(key = "branding_section"),
                                            animatedVisibilityScope = this@AnimatedContent,
                                            boundsTransform = brandingBoundsTransform,
                                            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                        ).skipToLookaheadSize()
                                )
                            }
                        }
                        Card(
                            modifier = Modifier
                                .weight(1.2f)
                                .then(
                                    with(sharedTransitionScope) {
                                        Modifier
                                            .sharedBounds(
                                                sharedContentState = rememberSharedContentState(key = "form_container"),
                                                animatedVisibilityScope = this@AnimatedContent,
                                                boundsTransform = formBoundsTransform,
                                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                            )
                                    }
                                ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            with(sharedTransitionScope) {
                                Box(modifier = Modifier.skipToLookaheadSize()) {
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
                    with(sharedTransitionScope) {
                        AuthBrandingSection(
                            modifier = Modifier
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "branding_section"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform = brandingBoundsTransform,
                                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                )
                                .skipToLookaheadSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.then(
                            with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "form_container"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform = formBoundsTransform,
                                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                )
                            }
                        )
                    ) {
                        with(sharedTransitionScope) {
                            Box(modifier = Modifier.skipToLookaheadSize()) {
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
            }
        }
    }
}

@Composable
private fun AuthBrandingSection(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

        AnimatedContent(
            targetState = uiState.mode,
            transitionSpec = {
                if (targetState == AuthMode.Register) {
                    // 登录 -> 注册：向左滑动（新页面从右侧滑入，旧页面向左侧滑出）
                    (slideInHorizontally { width -> width / 2 }
                            + fadeIn(animationSpec = tween(300))) togetherWith
                            (slideOutHorizontally { width -> -width / 2 }
                                    + fadeOut(animationSpec = tween(300)))
                } else {
                    // 注册 -> 登录：向右滑动（新页面从左侧滑入，旧页面向右侧滑出）
                    (slideInHorizontally { width -> -width / 2 }
                            + fadeIn(animationSpec = tween(300))) togetherWith
                            (slideOutHorizontally { width -> width / 2 }
                                    + fadeOut(animationSpec = tween(300)))
                }.using(
                    SizeTransform(clip = true)
                )
            },
            label = "FormFieldsTransition"
        ) { mode ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                var passwordVisible by remember { mutableStateOf(false) }
                if (mode == AuthMode.Login) {
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
                } else {
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
            }
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
