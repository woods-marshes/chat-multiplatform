package com.github.woodsmarshes.chat.feature.auth.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.ArcMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.github.woodsmarshes.chat.core.ui.components.AppTabRow
import com.github.woodsmarshes.chat.core.ui.components.AppTextField
import com.github.woodsmarshes.chat.core.ui.components.ButtonSize
import com.github.woodsmarshes.chat.core.ui.components.ButtonStyle
import com.github.woodsmarshes.chat.core.ui.components.ChatAppButton
import com.github.woodsmarshes.chat.core.ui.components.ChatAppCard
import com.github.woodsmarshes.chat.core.ui.components.ScreenEdgeGlow
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarHost
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.utils.rememberScreenCornerRadius
import com.github.woodsmarshes.chat.feature.auth.model.AuthMode
import com.github.woodsmarshes.chat.feature.auth.model.AuthScreenState
import com.github.woodsmarshes.chat.feature.auth.model.AuthUiState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = koinViewModel(),
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.screenState) {
        when (val state = uiState.screenState) {
            // Success intentionally has no side effect here: the persisted
            // token flips the app-level session flow, which swaps this screen
            // out for the authenticated content.
            is AuthScreenState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetScreenState()
            }
            else -> {}
        }
    }

    SharedTransitionLayout(modifier = modifier) {
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
        if (!isDesktopOrTablet && uiState.screenState == AuthScreenState.Loading) {
            ScreenEdgeGlow(
                enabled = true,
                cornerRadius = rememberScreenCornerRadius(),
            )
        }
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
                        ChatAppCard(
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
                            cornerRadius = 16.dp,
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
        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
    val onSubmitWithClearFocus: () -> Unit = {
        focusManager.clearFocus()
        onSubmit()
    }

    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val strings = LocalStrings.current
        Text(
            text = if (uiState.mode == AuthMode.Login) strings.authWelcomeBack else strings.createAccount,
            style = MaterialTheme.typography.headlineSmall
        )

        AppTabRow(
            tabs = listOf(strings.login, strings.register),
            selectedTabIndex = if (uiState.mode == AuthMode.Login) 0 else 1,
            onTabSelected = { index ->
                onModeChange(if (index == 0) AuthMode.Login else AuthMode.Register)
            },
            withContour = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedContent(
            targetState = uiState.mode,
            transitionSpec = {
                if (targetState == AuthMode.Register) {
                    (slideInHorizontally { width -> width / 2 }
                            + fadeIn(animationSpec = tween(300))) togetherWith
                            (slideOutHorizontally { width -> -width / 2 }
                                    + fadeOut(animationSpec = tween(300)))
                } else {
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
            val strings = LocalStrings.current
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                var passwordVisible by rememberSaveable { mutableStateOf(false) }
                var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
                if (mode == AuthMode.Login) {
                    AppTextField(
                        value = uiState.email,
                        onValueChange = onEmailChange,
                        label = strings.emailLabel,
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.emailError != null,
                        errorText = uiState.emailError?.localizedMessage(),
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                focusManager.moveFocus(FocusDirection.Down)
                            }
                        )
                    )

                    AppTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChange,
                        label = strings.passwordLabel,
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.passwordError != null,
                        errorText = uiState.passwordError?.localizedMessage(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Default.Visibility
                                    } else {
                                        Icons.Default.VisibilityOff
                                    },
                                    contentDescription = if (passwordVisible) strings.hidePasswordCd else strings.showPasswordCd
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (uiState.canSubmit) onSubmitWithClearFocus() }
                        )
                    )
                } else {
                    AppTextField(
                        value = uiState.name,
                        onValueChange = onNameChange,
                        label = strings.nameLabel,
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.nameError != null,
                        errorText = uiState.nameError?.localizedMessage(),
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                focusManager.moveFocus(FocusDirection.Down)
                            }
                        )
                    )

                    AppTextField(
                        value = uiState.email,
                        onValueChange = onEmailChange,
                        label = strings.emailLabel,
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.emailError != null,
                        errorText = uiState.emailError?.localizedMessage(),
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                focusManager.moveFocus(FocusDirection.Down)
                            }
                        )
                    )

                    AppTextField(
                        value = uiState.password,
                        onValueChange = onPasswordChange,
                        label = strings.passwordLabel,
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.passwordError != null,
                        errorText = uiState.passwordError?.localizedMessage(),
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) strings.hidePasswordCd else strings.showPasswordCd,
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    AppTextField(
                        value = uiState.confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        label = strings.confirmPasswordLabel,
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.confirmPasswordError != null,
                        errorText = uiState.confirmPasswordError?.localizedMessage(),
                        leadingIcon = { Icon(Icons.Default.CheckCircle, null) },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (confirmPasswordVisible) strings.hidePasswordCd else strings.showPasswordCd,
                                )
                            }
                        },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (uiState.canSubmit) onSubmitWithClearFocus()
                        })
                    )
                }
            }
        }

        ChatAppButton(
            onClick = {
                if (uiState.screenState !is AuthScreenState.Loading) {
                    onSubmitWithClearFocus()
                }
            },
            label = if (uiState.mode == AuthMode.Login) strings.login else strings.createAccount,
            style = ButtonStyle.PRIMARY,
            size = ButtonSize.LG,
            enabled = uiState.canSubmit,
            isLoading = uiState.screenState is AuthScreenState.Loading,
            fullWidth = true,
        )
    }
}

@Composable
private fun com.github.woodsmarshes.chat.feature.auth.model.AuthValidationError.localizedMessage(): String {
    val strings = LocalStrings.current
    return when (this) {
        com.github.woodsmarshes.chat.feature.auth.model.AuthValidationError.NameEmpty -> strings.authNameEmpty
        com.github.woodsmarshes.chat.feature.auth.model.AuthValidationError.NameShort -> strings.authNameShort
        com.github.woodsmarshes.chat.feature.auth.model.AuthValidationError.NameLong -> strings.authNameLong
        com.github.woodsmarshes.chat.feature.auth.model.AuthValidationError.EmailEmpty -> strings.authEmailEmpty
        com.github.woodsmarshes.chat.feature.auth.model.AuthValidationError.PasswordEmpty -> strings.authPasswordEmpty
        com.github.woodsmarshes.chat.feature.auth.model.AuthValidationError.PasswordShort -> strings.authWeakPassword
        com.github.woodsmarshes.chat.feature.auth.model.AuthValidationError.ConfirmEmpty -> strings.authConfirmEmpty
        com.github.woodsmarshes.chat.feature.auth.model.AuthValidationError.PasswordMismatch -> strings.authPasswordMismatch
    }
}
