package com.github.woodsmarshes.chat.exceptions

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import com.github.woodsmarshes.chat.core.model.error.DomainError

class AppException(val error: DomainError) : RuntimeException()

fun <V, E : DomainError> Result<V, E>.getOrThrow(): V {
    return getOrThrow { throw AppException(it) }
}

class AuthenticationException(message: String) : RuntimeException(message)