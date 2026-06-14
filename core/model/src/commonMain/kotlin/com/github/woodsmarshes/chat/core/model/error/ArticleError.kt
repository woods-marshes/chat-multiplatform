package com.github.woodsmarshes.chat.core.model.error

import kotlinx.serialization.Serializable

@Serializable
sealed interface ArticleError : DomainError {
    @Serializable data object NotFound : ArticleError
    @Serializable data object PermissionDenied : ArticleError
    @Serializable data object OperationFailed : ArticleError
    @Serializable data class Unknown(override val message: String? = null) : ArticleError
}
