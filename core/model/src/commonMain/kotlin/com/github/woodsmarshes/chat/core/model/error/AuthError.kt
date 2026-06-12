package com.github.woodsmarshes.chat.core.model.error

import kotlinx.serialization.Serializable

@Serializable
sealed interface AuthError : DomainError {
    @Serializable data object InvalidCredentials : AuthError
    @Serializable data object UserAlreadyExists : AuthError
    @Serializable data object WeakPassword : AuthError
    @Serializable data object InsertionFailed : AuthError
    @Serializable data class Unknown(override val message: String? = null) : AuthError
}