package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.events.SyncStateResponse
import com.github.woodsmarshes.chat.exceptions.getOrThrow
import com.github.woodsmarshes.chat.service.SyncService
import com.github.woodsmarshes.chat.utils.extractUserId
import io.ktor.server.auth.authenticate
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import kotlin.time.Clock

fun Route.syncRoutes() {
    val syncService by inject<SyncService>()

    authenticate {
        get<V1.Sync.Heads> {
            val userId = call.extractUserId()
            val entries = syncService.getHeads(userId)
            call.respond(
                SyncStateResponse.Heads(
                    entries = entries,
                    timestamp = Clock.System.now(),
                )
            )
        }

        get<V1.Sync.Changes> { params ->
            val userId = call.extractUserId()
            val batch = syncService.getChanges(
                callerUserId = userId,
                scope = params.scope,
                id = params.id,
                afterRevision = params.afterRevision,
                limit = params.limit,
            ).getOrThrow()
            call.respond(batch)
        }

        get<V1.Sync.Snapshot> { params ->
            val userId = call.extractUserId()
            val snapshot = syncService.getSnapshot(
                callerUserId = userId,
                scope = params.scope,
                id = params.id,
                limit = params.limit,
            ).getOrThrow()
            call.respond(snapshot)
        }
    }
}
