package com.github.woodsmarshes.chat.utils

import com.github.woodsmarshes.chat.exceptions.AuthenticationException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.uuid.Uuid

/**
 * Runs [block] inside an Exposed transaction. This is the only way any
 * repository touches the database, and it has one property the whole
 * layering rests on: when a transaction is already open for this coroutine
 * (a service opened one via [inTransaction]), the block JOINS it instead of
 * opening its own — repository calls inside a service unit of work commit
 * and roll back together. Never replace a repository's dbQuery with
 * newSuspendedTransaction: it would break those units of work silently.
 */
suspend fun <T> dbQuery(block: suspend () -> T): T =
    withContext(Dispatchers.IO) {
        suspendTransaction { block() }
    }

/**
 * A service-level unit of work: several repository calls that must commit
 * or roll back as one. Implemented by [dbQuery], so every repository call
 * inside joins this transaction — see [dbQuery] for the contract that makes
 * that work.
 */
suspend fun <T> inTransaction(block: suspend () -> T): T = dbQuery(block)


fun ApplicationCall.extractUserId(): Uuid {
    val principal = this.principal<JWTPrincipal>()
    val claim = principal?.getClaim(Keys.USER_ID, String::class)
        ?: throw AuthenticationException("Invalid or missing User ID in Token")
    return Uuid.parseOrNull(claim)
        ?: throw AuthenticationException("Invalid User ID in Token")
}

fun ApplicationCall.extractUserIdFromWebSocket(): Uuid? {
    val principal = this.principal<JWTPrincipal>()
    val idString = principal?.getClaim(Keys.USER_ID, String::class).toString()
    return Uuid.parseOrNull(idString)
}


