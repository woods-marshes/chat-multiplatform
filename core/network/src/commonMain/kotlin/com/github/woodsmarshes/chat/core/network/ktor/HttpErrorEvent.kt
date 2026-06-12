package com.github.woodsmarshes.chat.core.network.ktor

import io.ktor.http.HttpStatusCode

sealed class HttpErrorEvent {
    data class Unauthorized(
        val requestUrl: String,
    ) : HttpErrorEvent()

    data class GeneralHttpError(
        val requestUrl: String,
        val statusCode: HttpStatusCode,
        val responseBody: String
    ) : HttpErrorEvent()
}