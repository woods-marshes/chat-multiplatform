package com.github.woodsmarshes.chat.app.session

import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.dsl.module
import kotlin.time.Duration.Companion.milliseconds

val sessionModule = module {
    single(createdAtStart = true) { SessionManager(get(), get(), get(), get()) }
}

/**
 * Single owner of the session lifecycle: persisted auth state, the per-user
 * database and the realtime socket.
 *
 * The session flow is collected eagerly in a process-scoped scope, so the
 * database is opened (and the socket connected) regardless of whether any UI
 * is currently subscribed — a background sync must never hit an unopened
 * database.
 *
 * Logout is an explicit sequence: disconnect the socket, clear the persisted
 * session (which flips [isLoggedIn] and tears down the authenticated UI, its
 * Nav3 back stacks and entry ViewModels), then wait out a grace period before
 * closing the database so in-flight queries from those collectors are not cut
 * off underneath. The close is guarded by user id: if another account signed
 * in during the grace window, their freshly opened database is left alone.
 */
class SessionManager(
    private val authRepository: AuthRepository,
    private val userSettingDataSource: UserSettingDataSource,
    private val realtimeApi: RealtimeApi,
    private val databaseHolder: DatabaseHolder,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /** null = persisted state still loading; the UI shows a loading gate. */
    val isLoggedIn: StateFlow<Boolean?> = authRepository.observeIsLoggedIn()
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

    /** Cached user of the current session (for the navigation rail avatar). */
    val currentUser: StateFlow<User?> = userSettingDataSource.user
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

    val connectionState: StateFlow<ConnectionState> = realtimeApi.connectionState

    init {
        scope.launch {
            isLoggedIn.filterNotNull().collect { loggedIn ->
                when (loggedIn) {
                    true -> {
                        // Open the per-user database before the socket comes
                        // up: realtime events write straight into it.
                        userSettingDataSource.user.first()?.let { user ->
                            databaseHolder.getOrCreateDatabase(user.id)
                        }
                        realtimeApi.connect()
                    }
                    false -> realtimeApi.disconnect()
                }
            }
        }
    }

    fun logout() {
        scope.launch {
            realtimeApi.disconnect()
            val userId = userSettingDataSource.user.first()?.id
            authRepository.logout()
            delay(LOGOUT_DB_CLOSE_GRACE)
            userId?.let { databaseHolder.closeDatabaseIfCurrent(it) }
        }
    }

    private companion object {
        val LOGOUT_DB_CLOSE_GRACE = 500.milliseconds
    }
}
