package com.github.woodsmarshes.chat.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.woodsmarshes.chat.LocalAppState
import com.varabyte.kobweb.compose.css.StyleVariable
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.data.add
import com.varabyte.kobweb.core.init.InitRoute
import com.varabyte.kobweb.core.init.InitRouteContext
import com.varabyte.kobweb.core.layout.Layout
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.breakpoint.displayIfAtLeast
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.ColorPalettes
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.fr
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import com.github.woodsmarshes.chat.UncoloredButtonVariant
import com.github.woodsmarshes.chat.components.layouts.PageLayoutData
import com.github.woodsmarshes.chat.components.widgets.AuthModeToggle
import com.github.woodsmarshes.chat.components.widgets.CircularProgressIndicator
import com.github.woodsmarshes.chat.model.viewmodel.login.AuthScreenState
import com.github.woodsmarshes.chat.model.viewmodel.login.LoginUiState
import com.github.woodsmarshes.chat.model.viewmodel.login.LoginViewModel
import com.github.woodsmarshes.chat.rememberKoinInstance
import com.github.woodsmarshes.chat.toSitePalette
import com.varabyte.kobweb.compose.css.AlignItems
import com.varabyte.kobweb.compose.css.BoxShadow
import com.varabyte.kobweb.compose.css.JustifyContent
import com.varabyte.kobweb.compose.css.functions.clamp
import com.varabyte.kobweb.compose.css.setVariable
import com.varabyte.kobweb.compose.dom.ref
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.compose.ui.thenIf
import com.varabyte.kobweb.silk.components.forms.InputGroup
import com.varabyte.kobweb.silk.components.forms.InputVars
import com.varabyte.kobweb.silk.components.forms.OutlinedInputVariant
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.icons.fa.FaEye
import com.varabyte.kobweb.silk.components.icons.fa.FaEyeSlash
import com.varabyte.kobweb.silk.components.layout.Surface
import com.varabyte.kobweb.silk.components.overlay.Overlay
import com.varabyte.kobweb.silk.components.overlay.OverlayVars
import com.varabyte.kobweb.silk.theme.colors.palette.background
import com.varabyte.kobweb.silk.theme.colors.palette.border
import com.varabyte.kobweb.silk.theme.colors.palette.overlay
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.onSubmit
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Form

val LoginPageStyle = CssStyle.base {
    Modifier
        .fillMaxSize()
        .padding(1.cssRem)
        .display(DisplayStyle.Flex)
        .justifyContent(JustifyContent.Center)
        .alignItems(AlignItems.Center)
}

val FormContainerStyle = CssStyle.base {
    val palette = colorMode.toPalette()
    Modifier
        .width(clamp(320.px, 80.percent, 480.px))
        .backgroundColor(palette.background)
        .padding(2.cssRem)
        .borderRadius(1.cssRem)
        .boxShadow(
            BoxShadow.of(
                offsetX = 0.px,
                offsetY = 2.px,
                blurRadius = 4.px,
                color = Colors.Black.copyf(alpha = 0.04f)
            ),
            BoxShadow.of(
                offsetX = 0.px,
                offsetY = 8.px,
                blurRadius = 24.px,
                color = Colors.Black.copyf(alpha = 0.08f)
            )
        )
        .gap(1.5.cssRem)
}

// Container that has a tagline and grid on desktop, and just the tagline on mobile
val HeroContainerStyle = CssStyle {
    base { Modifier.fillMaxWidth().gap(2.cssRem) }
    Breakpoint.MD { Modifier.margin { top(20.vh) } }
}

// A demo grid that appears on the homepage because it looks good
val HomeGridStyle = CssStyle.base {
    Modifier
        .gap(0.5.cssRem)
        .width(70.cssRem)
        .height(18.cssRem)
}

private val GridCellColorVar by StyleVariable<Color>()
val HomeGridCellStyle = CssStyle.base {
    Modifier
        .backgroundColor(GridCellColorVar.value())
        .boxShadow(blurRadius = 0.6.cssRem, color = GridCellColorVar.value())
        .borderRadius(1.cssRem)
}

@Composable
private fun GridCell(color: Color, row: Int, column: Int, width: Int? = null, height: Int? = null) {
    Div(
        HomeGridCellStyle.toModifier()
            .setVariable(GridCellColorVar, color)
            .gridItem(row, column, width, height)
            .toAttrs()
    )
}

@InitRoute
fun initHomePage(ctx: InitRouteContext) {
    ctx.data.add(PageLayoutData("Home"))
}


@Page
@Layout(".components.layouts.PageLayout")
@Composable
fun HomePage() {
    val appState = LocalAppState.current

    val viewModel = rememberKoinInstance<LoginViewModel>()

    val loginState by viewModel.loginUiState.collectAsState()
    val registerState by viewModel.registerUiState.collectAsState()
    val authMode by viewModel.currentScreen.collectAsState()

    val name by viewModel.name.collectAsState()
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val confirmPassword by viewModel.confirmPassword.collectAsState()

    val nameError by viewModel.nameError.collectAsState()
    val emailError by viewModel.emailError.collectAsState()
    val passwordError by viewModel.passwordError.collectAsState()
    val confirmPasswordError by viewModel.confirmPasswordError.collectAsState()

    val uiState = if (authMode == AuthScreenState.LOGIN) loginState else registerState
    val pageContext = rememberPageContext()

    val isLogIn by appState.isLogIn.collectAsState()
    val isOffline by appState.isOffline.collectAsState()

    LaunchedEffect(isLogIn, isOffline) {
        if (isLogIn == true && !isOffline) {
            pageContext.router.tryRoutingTo("/chat")
        }
    }

    LaunchedEffect(loginState, registerState) {
        if (loginState is LoginUiState.Success || registerState is LoginUiState.Success) {
            pageContext.router.tryRoutingTo("/chat")
        }
    }
    Box(LoginPageStyle.toModifier()) {
        Surface(FormContainerStyle.toModifier()) {
            Column(
                modifier = Modifier.fillMaxWidth().gap(1.5.cssRem),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            AuthModeToggle(authMode) { newMode -> viewModel.updateCurrentScreen(newMode) }
            Form(attrs = {
                onSubmit {
                    it.preventDefault() // 阻止页面刷新
                    if (authMode == AuthScreenState.LOGIN) viewModel.login() else viewModel.register()
                }
            }) {
                Column(
                    modifier = Modifier.gap(1.25.cssRem),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (authMode == AuthScreenState.REGISTER) {
                        TextInputWithValidation(
                            label = "Name",
                            text = name,
                            onTextChange = { viewModel.updateName(it) },
                            error = nameError,
                            type = InputType.Text,
                            ref = { it.focus() } // 注册时自动聚焦
                        )
                    }

                    // --- 通用字段 ---
                    TextInputWithValidation(
                        label = "Email",
                        text = email,
                        onTextChange = { viewModel.updateEmail(it) },
                        error = emailError,
                        type = InputType.Text,
                        ref = { if (authMode == AuthScreenState.LOGIN) it.focus() } // 登录时自动聚焦
                    )

                    PasswordInputWithValidation(
                        label = "Password",
                        text = password,
                        onTextChange = { viewModel.updatePassword(it) },
                        error = passwordError,
                    )

                    // --- 注册时才显示的字段 ---
                    if (authMode == AuthScreenState.REGISTER) {
                        PasswordInputWithValidation(
                            label = "Confirm Password",
                            text = confirmPassword,
                            onTextChange = { viewModel.updateConfirmPassword(it) },
                            error = confirmPasswordError,
                            isLastField = true // 最后一个字段，回车键触发提交
                        )
                    }

                    // 6. 通用错误提示
                    if (uiState is LoginUiState.Error) {
                        SpanText(
                            text = uiState.exception.message ?: "An unknown error occurred.",
                            modifier = Modifier.color(Colors.Red).padding(top = 0.5.cssRem)
                        )
                    }

                    Button(
                        onClick = { if (authMode == AuthScreenState.LOGIN) viewModel.login() else viewModel.register() },
                        modifier = Modifier.width(80.percent).height(48.px).margin(top = 1.cssRem),
                        enabled = uiState !is LoginUiState.Loading
                    ) {
                        SpanText(if (authMode == AuthScreenState.LOGIN) "Login" else "Register")
                    }
                }
            }

            }
        }


//        Div(
//            HomeGridStyle
//            .toModifier()
//            .displayIfAtLeast(Breakpoint.MD)
//            .grid {
//                rows { repeat(3) { size(1.fr) } }
//                columns { repeat(5) { size(1.fr) } }
//            }
//            .toAttrs()
//        ) {
//            val sitePalette = ColorMode.current.toSitePalette()
//            GridCell(sitePalette.brand.primary, 1, 1, 2, 2)
//            GridCell(ColorPalettes.Monochrome._600, 1, 3)
//            GridCell(ColorPalettes.Monochrome._100, 1, 4, width = 2)
//            GridCell(sitePalette.brand.accent, 2, 3, width = 2)
//            GridCell(ColorPalettes.Monochrome._300, 2, 5)
//            GridCell(ColorPalettes.Monochrome._800, 3, 1, width = 5)
//        }
    }

    if (uiState is LoginUiState.Loading) {
        Overlay(
            modifier = Modifier.setVariable(OverlayVars.BackgroundColor, ColorMode.current.toPalette().overlay),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun TextInputWithValidation(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    error: String?,
    type: InputType.Text,
    ref: (org.w3c.dom.HTMLInputElement) -> Unit = {}
) {
    val sitePalette = ColorMode.current.toSitePalette()
    Column(Modifier.fillMaxWidth()) {
        InputGroup(
            modifier = Modifier
                .fillMaxWidth()
                .thenIf(error != null) {
                    Modifier.setVariable(InputVars.BorderInvalidColor, sitePalette.brand.primary.inverted().toRgb().copyf(alpha = 0.5f))
                },
        ) {
            LeftAddon { SpanText(label) }
            Input(
                type = type,
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = label,
                variant = OutlinedInputVariant,
                valid = error == null,
                ref = ref { ref(it) }
            )
        }
        Box(Modifier.height(1.2.em).padding(top = 4.px)) {
            if (error != null) {
                SpanText(error, Modifier.color(sitePalette.brand.primary.inverted().toRgb().copyf(alpha = 0.8f)).fontSize(0.8.em))
            }
        }
    }
}

@Composable
private fun PasswordInputWithValidation(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    error: String?,
    isLastField: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val sitePalette = ColorMode.current.toSitePalette()

    Column(Modifier.fillMaxWidth()) {
        InputGroup(
            modifier = Modifier
                .fillMaxWidth()
                .thenIf(error != null) {
                    Modifier.setVariable(InputVars.BorderInvalidColor, sitePalette.brand.primary.inverted().toRgb().copyf(alpha = 0.5f))
                }
        ) {
            LeftAddon { SpanText(label) }
            TextInput(
                text = text,
                onTextChange = onTextChange,
                placeholder = label,
                variant = OutlinedInputVariant,
                password = !passwordVisible,
                valid = error == null
            )
            RightInset {
                Button(
                    onClick = { passwordVisible = !passwordVisible },
                    variant = UncoloredButtonVariant
                ) {
                    if (passwordVisible) FaEyeSlash() else FaEye()
                }
            }
        }
        Box(Modifier.height(1.2.em).padding(top = 4.px)) {
            if (error != null) {
                SpanText(error, Modifier.color(sitePalette.brand.primary.inverted().toRgb().copyf(alpha = 0.8f)).fontSize(0.8.em))
            }
        }
    }
}

