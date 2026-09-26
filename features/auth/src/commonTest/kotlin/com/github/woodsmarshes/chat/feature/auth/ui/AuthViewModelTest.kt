package com.github.woodsmarshes.chat.feature.auth.ui

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.feature.auth.model.AuthMode
import com.github.woodsmarshes.chat.feature.auth.model.AuthScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The name and confirm-password fields are not rendered in Login mode, so a validation
     * error produced in Register mode can never be cleared again once the user switches back.
     * canSubmit treats any remaining error as a hard block, which used to disable the login
     * button for the rest of the session.
     */
    @Test
    fun switchingBackToLoginDropsRegisterOnlyValidationErrors() {
        val vm = AuthViewModel(FakeAuthRepository())

        vm.setMode(AuthMode.Register)
        vm.updateName("a")
        assertNotNull(vm.uiState.value.nameError, "a one character name must be rejected in Register mode")

        vm.updateEmail("user@example.com")
        vm.updatePassword("secret1")
        vm.updateConfirmPassword("secret1")

        vm.setMode(AuthMode.Login)

        assertNull(vm.uiState.value.nameError)
        assertNull(vm.uiState.value.confirmPasswordError)
        assertTrue(vm.uiState.value.canSubmit, "login must be submittable after switching back from Register")
    }

    @Test
    fun switchingBackToRegisterKeepsRegisterValidationErrors() {
        val vm = AuthViewModel(FakeAuthRepository())

        vm.setMode(AuthMode.Register)
        vm.updateName("a")
        vm.setMode(AuthMode.Login)
        vm.setMode(AuthMode.Register)

        assertNotNull(vm.uiState.value.nameError, "the name is still invalid, the error must be reported again")
        assertFalse(vm.uiState.value.canSubmit)
    }

    /**
     * The submit button reads the screen state captured at composition time, so two taps
     * inside one frame both reach [AuthViewModel.submit] while the first request is still in
     * flight. A standard dispatcher keeps that window open, which is what happens in the app
     * while the login call is waiting on the network.
     */
    @Test
    fun submittingTwiceOnlyAuthenticatesOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeAuthRepository()
        val vm = AuthViewModel(repository)

        vm.updateEmail("user@example.com")
        vm.updatePassword("secret1")

        vm.submit()
        assertEquals(AuthScreenState.Loading, vm.uiState.value.screenState)
        vm.submit()

        advanceUntilIdle()

        assertEquals(1, repository.loginCalls.size, "a second tap must not start a second login request")
        assertEquals(AuthScreenState.Success(repository.user), vm.uiState.value.screenState)
    }

    private class FakeAuthRepository : AuthRepository {

        val user = User(
            id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
            username = "tester",
            email = "user@example.com",
            displayName = "Tester",
            avatarUrl = null,
            bio = null,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
            deletedAt = null,
        )

        val loginCalls = mutableListOf<Pair<String, String>>()

        override val jwtToken: Flow<String?> = MutableStateFlow(null)

        override fun observeIsLoggedIn(): Flow<Boolean> = MutableStateFlow(false)

        override suspend fun login(email: String, password: String): Result<User, AuthError> {
            loginCalls += email to password
            return Ok(user)
        }

        override suspend fun register(
            username: String,
            email: String,
            password: String,
        ): Result<User, AuthError> = Ok(user)

        override suspend fun logout() = Unit

        override suspend fun tryAutoLogin(): Result<User, AuthError> = Ok(user)
    }
}
