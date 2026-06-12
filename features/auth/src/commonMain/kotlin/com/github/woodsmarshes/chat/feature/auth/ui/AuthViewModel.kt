package com.github.woodsmarshes.chat.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.auth.model.AuthMode
import com.github.woodsmarshes.chat.feature.auth.model.AuthScreenState
import com.github.woodsmarshes.chat.feature.auth.model.AuthUiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setMode(mode: AuthMode) = _uiState.update { it.copy(mode = mode) }

    fun updateName(name: String) = _uiState.update { 
        it.copy(name = name, nameError = AuthUiState.validateName(name)) 
    }

    fun updateEmail(email: String) = _uiState.update { 
        it.copy(email = email, emailError = AuthUiState.validateEmail(email)) 
    }

    fun updatePassword(password: String) = _uiState.update { 
        val newState = it.copy(password = password, passwordError = AuthUiState.validatePassword(password))
        if (it.mode == AuthMode.Register) {
            newState.copy(confirmPasswordError = AuthUiState.validateConfirmPassword(it.confirmPassword, password))
        } else newState
    }

    fun updateConfirmPassword(confirm: String) = _uiState.update { 
        it.copy(confirmPassword = confirm, confirmPasswordError = AuthUiState.validateConfirmPassword(confirm, it.password)) 
    }

    fun resetScreenState() = _uiState.update { it.copy(screenState = AuthScreenState.Idle) }

    fun submit() {
        if (!validateAll()) return

        val s = _uiState.value
        _uiState.update { it.copy(screenState = AuthScreenState.Loading) }

        viewModelScope.launch {
            val result = when (s.mode) {
                AuthMode.Login -> authRepository.login(s.email, s.password)
                AuthMode.Register -> authRepository.register(s.name, s.email, s.password)
            }

            result.onOk { user ->
                _uiState.update { it.copy(screenState = AuthScreenState.Success(user)) }
            }.onErr { error ->
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
                confirmPasswordError = if (s.mode == AuthMode.Register) AuthUiState.validateConfirmPassword(s.confirmPassword, s.password) else null
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
            else -> strings.authOperationFailed
        }
    }
}