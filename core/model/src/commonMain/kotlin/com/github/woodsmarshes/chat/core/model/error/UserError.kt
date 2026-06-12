package com.github.woodsmarshes.chat.core.model.error

import kotlinx.serialization.Serializable

@Serializable
sealed interface UserError : DomainError {
    @Serializable data object NotFound : UserError
    @Serializable data object PermissionDenied : UserError
    @Serializable data object UpdateFailed : UserError
    @Serializable data object InvalidRequest : UserError

    @Serializable data class Unknown(override val message: String? = null) : UserError
}