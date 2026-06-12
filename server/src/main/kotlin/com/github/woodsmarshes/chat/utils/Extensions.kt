package com.github.woodsmarshes.chat.utils

import com.github.woodsmarshes.chat.exceptions.AuthenticationException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.uuid.Uuid

suspend fun <T> dbQuery(block: suspend () -> T): T =
    withContext(Dispatchers.IO) {
        suspendTransaction { block() }
    }


fun ApplicationCall.extractUserId(): Uuid {
    val principal = this.principal<JWTPrincipal>()
    val a = principal?.getClaim(Keys.USER_ID, String::class)
        ?: throw AuthenticationException("Invalid or missing User ID in Token")
    return Uuid.parse(a)
}

fun ApplicationCall.extractUserIdFromWebSocket(): Uuid? {
    val principal = this.principal<JWTPrincipal>()
    val idString = principal?.getClaim(Keys.USER_ID, String::class).toString()
    return Uuid.parseOrNull(idString)
}


