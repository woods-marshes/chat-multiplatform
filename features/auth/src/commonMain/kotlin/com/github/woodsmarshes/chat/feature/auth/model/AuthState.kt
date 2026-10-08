package com.github.woodsmarshes.chat.feature.auth.model

import com.github.woodsmarshes.chat.core.model.User

enum class AuthValidationError {
    NameEmpty, NameShort, NameLong, EmailEmpty, PasswordEmpty, PasswordShort, ConfirmEmpty, PasswordMismatch
}

sealed interface AuthMode {
    data object Login : AuthMode
    data object Register : AuthMode
}

sealed interface AuthScreenState {
    data object Idle : AuthScreenState
    data object Loading : AuthScreenState
    data class Success(val user: User) : AuthScreenState
    data class Error(val message: String) : AuthScreenState
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: AuthValidationError? = null,
    val emailError: AuthValidationError? = null,
    val passwordError: AuthValidationError? = null,
    val confirmPasswordError: AuthValidationError? = null,
    val screenState: AuthScreenState = AuthScreenState.Idle
) {
    override fun toString(): String =
        "AuthUiState(mode=$mode, name=$name, email=$email, password=<redacted>, confirmPassword=<redacted>, " +
            "nameError=$nameError, emailError=$emailError, passwordError=$passwordError, " +
            "confirmPasswordError=$confirmPasswordError, screenState=$screenState)"

    val canSubmit: Boolean get() {
        val hasErrors = nameError != null || emailError != null ||
                passwordError != null || confirmPasswordError != null
        val fieldsFilled = when (mode) {
            AuthMode.Login -> email.isNotBlank() && password.isNotBlank()
            AuthMode.Register -> name.isNotBlank() && email.isNotBlank() &&
                    password.isNotBlank() && confirmPassword.isNotBlank()
        }
        return !hasErrors && fieldsFilled
    }

    companion object {
        fun validateName(name: String) = when {
            name.isEmpty() -> AuthValidationError.NameEmpty
            name.length < 2 -> AuthValidationError.NameShort
            name.length > 15 -> AuthValidationError.NameLong
            else -> null
        }

        fun validateEmail(email: String) = if (email.isEmpty()) AuthValidationError.EmailEmpty else null

        fun validatePassword(password: String) = when {
            password.isEmpty() -> AuthValidationError.PasswordEmpty
            password.length < 6 -> AuthValidationError.PasswordShort
            else -> null
        }

        fun validateConfirmPassword(confirm: String, password: String) = when {
            confirm.isEmpty() -> AuthValidationError.ConfirmEmpty
            confirm != password -> AuthValidationError.PasswordMismatch
            else -> null
        }
    }
}