package com.github.woodsmarshes.chat.feature.auth.model

import com.github.woodsmarshes.chat.core.model.User

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
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val screenState: AuthScreenState = AuthScreenState.Idle
) {
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
            name.isEmpty() -> "Name cannot be empty"
            name.length < 2 -> "Name must be at least 2 characters"
            name.length > 15 -> "Name must be less than 15 characters"
            else -> null
        }

        fun validateEmail(email: String) = if (email.isEmpty()) "Email cannot be empty" else null

        fun validatePassword(password: String) = when {
            password.isEmpty() -> "Password cannot be empty"
            password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }

        fun validateConfirmPassword(confirm: String, password: String) = when {
            confirm.isEmpty() -> "Confirm password cannot be empty"
            confirm != password -> "Passwords do not match"
            else -> null
        }
    }
}