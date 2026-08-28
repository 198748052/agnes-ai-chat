package com.agnesai.chat.ui.auth

import com.agnesai.chat.data.auth.AuthRepository
import com.agnesai.chat.data.auth.AuthState
import com.agnesai.chat.data.auth.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthRepository(
        var loginResult: Result<Unit> = Result.success(Unit),
        var registerResult: Result<Unit> = Result.success(Unit)
    ) : AuthRepository {
        val authStateFlow = MutableStateFlow<AuthState>(AuthState.LoggedOut)
        override val authState: Flow<AuthState> = authStateFlow
        var loginCalls = 0
        var registerCalls = 0
        var lastUsername = ""
        var lastPassword = ""

        override suspend fun login(username: String, password: String): Result<Unit> {
            loginCalls++
            lastUsername = username
            lastPassword = password
            return loginResult
        }

        override suspend fun register(username: String, password: String): Result<Unit> {
            registerCalls++
            lastUsername = username
            lastPassword = password
            return registerResult
        }

        override suspend fun logout() {
            authStateFlow.value = AuthState.LoggedOut
        }

        override suspend fun updateProfile(nickname: String): Result<Unit> = Result.success(Unit)

        override suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun uploadAvatar(avatarBase64: String): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun loginSuccessClearsLoading() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.onUsernameChange("bob")
        vm.onPasswordChange("secret1")

        vm.login()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoggingIn)
        assertNull(state.error)
        assertEquals("bob", repo.lastUsername)
    }

    @Test
    fun loginFailureShowsError() = runTest(dispatcher) {
        val repo = FakeAuthRepository(loginResult = Result.failure(Exception("账号或密码错误")))
        val vm = AuthViewModel(repo)

        vm.login()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoggingIn)
        assertEquals("账号或密码错误", state.error)
    }

    @Test
    fun registerSuccessSwitchesToLoginMode() = runTest(dispatcher) {
        val repo = FakeAuthRepository(registerResult = Result.success(Unit))
        val vm = AuthViewModel(repo)
        vm.switchToRegister()
        vm.onUsernameChange("carol")
        vm.onPasswordChange("secret1")

        vm.register()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isRegisterMode)
        assertFalse(state.isLoggingIn)
        assertEquals("注册成功，请登录", state.successMessage)
        assertEquals("", state.password)
    }

    @Test
    fun registerFailureShowsError() = runTest(dispatcher) {
        val repo = FakeAuthRepository(registerResult = Result.failure(Exception("账号已被占用")))
        val vm = AuthViewModel(repo)
        vm.switchToRegister()

        vm.register()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isRegisterMode)
        assertFalse(state.isLoggingIn)
        assertEquals("账号已被占用", state.error)
    }

    @Test
    fun submitInRegisterModeCallsRegister() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        vm.switchToRegister()

        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.registerCalls)
        assertEquals(0, repo.loginCalls)
    }

    @Test
    fun submitInLoginModeCallsLogin() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)

        vm.submit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.loginCalls)
        assertEquals(0, repo.registerCalls)
    }

    @Test
    fun logoutCallsRepository() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = AuthViewModel(repo)
        repo.authStateFlow.value = AuthState.LoggedIn(UserInfo("1", "bob", "Bob"))

        vm.logout()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthState.LoggedOut, repo.authStateFlow.value)
    }
}
