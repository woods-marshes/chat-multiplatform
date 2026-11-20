package com.github.woodsmarshes.chat.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.network.ktor.ServerEndpointProvider
import com.github.woodsmarshes.chat.core.network.ktor.parseServerAddress
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.auth.model.AuthMode
import com.github.woodsmarshes.chat.feature.auth.model.AuthScreenState
import com.github.woodsmarshes.chat.feature.auth.model.AuthUiState
import com.github.woodsmarshes.chat.feature.auth.model.AuthValidationError
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val serverEndpointProvider: ServerEndpointProvider,
) : ViewModel() {
    private val log = KotlinLogging.logger {}

    private val _uiState =
        MutableStateFlow(
            AuthUiState(serverAddress = serverEndpointProvider.current.baseUrl),
        )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Register-only validation results must follow the active mode: the name and
    // confirm-password fields are not rendered in Login mode, so an error produced in
    // Register mode could never be cleared again and would keep canSubmit false for the
    // rest of the session.
    fun setMode(mode: AuthMode) =
        _uiState.update {
            it.copy(
                mode = mode,
                nameError = if (mode == AuthMode.Register) AuthUiState.validateName(it.name) else null,
                confirmPasswordError =
                    if (mode == AuthMode.Register) {
                        AuthUiState.validateConfirmPassword(it.confirmPassword, it.password)
                    } else {
                        null
                    },
            )
        }

    fun updateName(name: String) =
        _uiState.update {
            it.copy(name = name, nameError = AuthUiState.validateName(name))
        }

    fun updateEmail(email: String) =
        _uiState.update {
            it.copy(email = email, emailError = AuthUiState.validateEmail(email))
        }

    fun updatePassword(password: String) =
        _uiState.update {
            val newState = it.copy(password = password, passwordError = AuthUiState.validatePassword(password))
            if (it.mode == AuthMode.Register) {
                newState.copy(confirmPasswordError = AuthUiState.validateConfirmPassword(it.confirmPassword, password))
            } else {
                newState
            }
        }

    fun updateConfirmPassword(confirm: String) =
        _uiState.update {
            it.copy(confirmPassword = confirm, confirmPasswordError = AuthUiState.validateConfirmPassword(confirm, it.password))
        }

    fun updateServerAddress(address: String) =
        _uiState.update {
            it.copy(
                serverAddress = address,
                serverError =
                    if (address.isBlank() || parseServerAddress(address).isSuccess) {
                        null
                    } else {
                        AuthValidationError.ServerInvalid
                    },
            )
        }

    fun resetScreenState() = _uiState.update { it.copy(screenState = AuthScreenState.Idle) }

    fun submit() {
        // The submit button reads the screen state captured at composition time, so two taps
        // inside one frame would both pass the UI guard. Reject the duplicate here.
        if (_uiState.value.screenState is AuthScreenState.Loading) return
        log.info { "[AuthViewModel]: submit()" }
        if (!validateAll()) return

        val s = _uiState.value
        log.info { "[AuthViewModel]: submitting mode=${s.mode}" }

        // Fail fast on an unparseable address before entering Loading, so the
        // double-submit guard and the UI spinner stay synchronous.
        if (parseServerAddress(s.serverAddress).isFailure) {
            _uiState.update {
                it.copy(
                    serverError = AuthValidationError.ServerInvalid,
                    screenState = AuthScreenState.Idle,
                )
            }
            return
        }
        _uiState.update { it.copy(screenState = AuthScreenState.Loading) }

        viewModelScope.launch {
            // Apply the selection in memory before any credential request so
            // the login call itself targets the entered endpoint. It is only
            // persisted once the server accepts the credentials; a failed
            // login rolls back to the previously saved address so a typo
            // cannot survive a restart.
            val endpointResult = serverEndpointProvider.stage(s.serverAddress)
            if (endpointResult.isFailure) {
                _uiState.update {
                    it.copy(
                        serverError = AuthValidationError.ServerInvalid,
                        screenState = AuthScreenState.Idle,
                    )
                }
                return@launch
            }

            val result =
                when (s.mode) {
                    AuthMode.Login -> authRepository.login(s.email, s.password)
                    AuthMode.Register -> authRepository.register(s.name, s.email, s.password)
                }

            result
                .onOk { user ->
                    serverEndpointProvider.commitStaged().onFailure { cause ->
                        // The session is already authenticated against the new
                        // endpoint; only the preference write failed.
                        log.warn(cause) { "[AuthViewModel]: failed to persist server selection" }
                    }
                    _uiState.update { it.copy(screenState = AuthScreenState.Success(user)) }
                }.onErr { error ->
                    serverEndpointProvider.revertStaged()
                    log.info { "[AuthViewModel]: error -> $error" }
                    _uiState.update { it.copy(screenState = AuthScreenState.Error(error.toMessage())) }
                }
        }
    }

    private fun validateAll(): Boolean {
        _uiState.update { s ->
            s.copy(
                nameError = if (s.mode == AuthMode.Register) AuthUiState.validateName(s.name) else null,
                emailError = AuthUiState.validateEmail(s.email),
                passwordError = AuthUiState.validatePassword(s.password),
                confirmPasswordError =
                    if (s.mode == AuthMode.Register) {
                        AuthUiState.validateConfirmPassword(s.confirmPassword, s.password)
                    } else {
                        null
                    },
            )
        }
        return _uiState.value.canSubmit
    }

    private fun AuthError.toMessage(): String {
        val strings = getLocaleStrings()
        return when (this) {
            AuthError.InvalidCredentials -> strings.authInvalidCredentials
            AuthError.UserAlreadyExists -> strings.authUserExists
            AuthError.WeakPassword -> strings.authWeakPassword
            AuthError.AccountBanned -> strings.authAccountBanned
            else -> strings.authOperationFailed
        }
    }
}
