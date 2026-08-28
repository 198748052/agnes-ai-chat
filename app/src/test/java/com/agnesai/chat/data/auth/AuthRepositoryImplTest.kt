package com.agnesai.chat.data.auth

import com.agnesai.chat.data.network.AvatarResponseDto
import com.agnesai.chat.data.network.ChangePasswordRequestDto
import com.agnesai.chat.data.network.LoginRequestDto
import com.agnesai.chat.data.network.LoginResponseDto
import com.agnesai.chat.data.network.RegisterRequestDto
import com.agnesai.chat.data.network.ServerApiService
import com.agnesai.chat.data.network.UpdateProfileRequestDto
import com.agnesai.chat.data.network.UploadAvatarRequestDto
import com.agnesai.chat.data.network.UserDto
import com.agnesai.chat.data.network.UserStatsDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class AuthRepositoryImplTest {

    private class FakeAuthStorage(
        initialToken: String = "",
        initialUser: UserInfo? = null
    ) : AuthStorage {
        var token: String = initialToken
        val authStateFlow = MutableStateFlow(
            if (initialToken.isBlank()) {
                AuthState.LoggedOut
            } else {
                AuthState.LoggedIn(initialUser ?: UserInfo(id = "u", username = "u", nickname = "u"))
            }
        )
        override val authState: Flow<AuthState> = authStateFlow
        override suspend fun getAuthToken(): String = token
        override suspend fun saveAuth(token: String, user: UserInfo) {
            this.token = token
            authStateFlow.value = AuthState.LoggedIn(user)
        }

        override suspend fun updateProfile(nickname: String, avatarUrl: String?) {
            val current = authStateFlow.value as? AuthState.LoggedIn ?: return
            authStateFlow.value = AuthState.LoggedIn(current.user.copy(nickname = nickname, avatarUrl = avatarUrl))
        }

        override suspend fun clearAuth() {
            token = ""
            authStateFlow.value = AuthState.LoggedOut
        }
    }

    private class FakeServerApiService : ServerApiService {
        var loginCalls = 0
        var registerCalls = 0
        var loginRequest: LoginRequestDto? = null
        var loginResult: Response<LoginResponseDto> =
            Response.success(LoginResponseDto("server-token", UserDto("1", "bob", "Bob")))
        var loginError: Throwable? = null
        var registerResult: Response<UserDto> =
            Response.success(UserDto("2", "carol", "Carol"))

        override suspend fun login(request: LoginRequestDto): Response<LoginResponseDto> {
            loginCalls++
            loginRequest = request
            loginError?.let { throw it }
            return loginResult
        }

        override suspend fun register(request: RegisterRequestDto): Response<UserDto> {
            registerCalls++
            return registerResult
        }

        override suspend fun logout(): Response<Unit> = Response.success(Unit)

        override suspend fun getLatestAnnouncement(): Response<com.agnesai.chat.data.network.AnnouncementDto> =
            Response.success(
                com.agnesai.chat.data.network.AnnouncementDto("a", "t", "c", "normal", 0L)
            )

        override suspend fun getAppVersion(): Response<com.agnesai.chat.data.network.UpdateInfoDto> =
            Response.success(
                com.agnesai.chat.data.network.UpdateInfoDto(1, "1.0", false, "", "")
            )

        override suspend fun generateImage(
            request: com.agnesai.chat.data.network.GenerationRequestDto
        ): Response<com.agnesai.chat.data.network.GenerationResponseDto> =
            Response.success(com.agnesai.chat.data.network.GenerationResponseDto("t"))

        override suspend fun generateVideo(
            request: com.agnesai.chat.data.network.GenerationRequestDto
        ): Response<com.agnesai.chat.data.network.GenerationResponseDto> =
            Response.success(com.agnesai.chat.data.network.GenerationResponseDto("t"))

        var updateProfileCalls = 0
        var updateProfileRequest: UpdateProfileRequestDto? = null
        var updateProfileResult: Response<UserDto> =
            Response.success(UserDto("1", "bob", "NewBob", null))
        var updateProfileError: Throwable? = null

        override suspend fun updateProfile(request: UpdateProfileRequestDto): Response<UserDto> {
            updateProfileCalls++
            updateProfileRequest = request
            updateProfileError?.let { throw it }
            return updateProfileResult
        }

        var changePasswordCalls = 0
        var changePasswordRequest: ChangePasswordRequestDto? = null
        var changePasswordResult: Response<Unit> = Response.success(Unit)
        var changePasswordError: Throwable? = null

        override suspend fun changePassword(request: ChangePasswordRequestDto): Response<Unit> {
            changePasswordCalls++
            changePasswordRequest = request
            changePasswordError?.let { throw it }
            return changePasswordResult
        }

        var uploadAvatarCalls = 0
        var uploadAvatarRequest: UploadAvatarRequestDto? = null
        var uploadAvatarResult: Response<AvatarResponseDto> =
            Response.success(AvatarResponseDto("/uploads/avatars/1.jpg"))
        var uploadAvatarError: Throwable? = null

        override suspend fun uploadAvatar(request: UploadAvatarRequestDto): Response<AvatarResponseDto> {
            uploadAvatarCalls++
            uploadAvatarRequest = request
            uploadAvatarError?.let { throw it }
            return uploadAvatarResult
        }

        override suspend fun getStats(): Response<UserStatsDto> = Response.success(UserStatsDto())
    }

    private fun buildRepo(
        storage: FakeAuthStorage,
        api: FakeServerApiService
    ) = AuthRepositoryImpl(storage, api)

    @Test
    fun adminLoginSucceedsLocallyWithoutCallingServer() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.login("admin", "admin")

        assertTrue(result.isSuccess)
        assertEquals("admin-local-token", storage.token)
        assertEquals(0, api.loginCalls)
    }

    @Test
    fun adminLoginWrongPasswordFails() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.login("admin", "wrong")

        assertTrue(result.isFailure)
        assertEquals("超级账号密码错误", result.exceptionOrNull()?.message)
        assertFalse(storage.token.isNotBlank())
        assertEquals(0, api.loginCalls)
    }

    @Test
    fun normalUserLoginCallsServerAndSavesToken() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.login("bob", "secret1")

        assertTrue(result.isSuccess)
        assertEquals("server-token", storage.token)
        assertEquals(1, api.loginCalls)
        assertEquals("bob", api.loginRequest?.username)
    }

    @Test
    fun normalUserLoginUnauthorizedFails() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        api.loginResult = Response.error(
            401,
            "{\"detail\":\"invalid credentials\"}".toResponseBody(null)
        )
        val repo = buildRepo(storage, api)

        val result = repo.login("bob", "wrong")

        assertTrue(result.isFailure)
        assertFalse(storage.token.isNotBlank())
    }

    @Test
    fun normalUserLoginNetworkErrorFailsAndKeepsLoggedOut() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        api.loginError = IOException("timeout")
        val repo = buildRepo(storage, api)

        val result = repo.login("bob", "secret1")

        assertTrue(result.isFailure)
        assertFalse(storage.token.isNotBlank())
    }

    @Test
    fun registerShortPasswordFailsLocally() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.register("carol", "123")

        assertTrue(result.isFailure)
        assertEquals(0, api.registerCalls)
    }

    @Test
    fun registerReservedAdminNameFails() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.register("admin", "secret1")

        assertTrue(result.isFailure)
        assertEquals("该账号已存在", result.exceptionOrNull()?.message)
        assertEquals(0, api.registerCalls)
    }

    @Test
    fun registerCallsServerOnValidInput() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.register("carol", "secret1")

        assertTrue(result.isSuccess)
        assertEquals(1, api.registerCalls)
    }

    @Test
    fun registerDuplicateReturns409() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        api.registerResult = Response.error(
            409,
            "{\"detail\":\"username already exists\"}".toResponseBody(null)
        )
        val repo = buildRepo(storage, api)

        val result = repo.register("carol", "secret1")

        assertTrue(result.isFailure)
        assertEquals("账号已被占用", result.exceptionOrNull()?.message)
    }

    @Test
    fun logoutClearsAuth() = runTest {
        val storage = FakeAuthStorage(initialToken = "some-token")
        val repo = buildRepo(storage, FakeServerApiService())

        repo.logout()

        assertEquals("", storage.token)
    }

    @Test
    fun loginSurfacesMappedServerDetail() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        api.loginResult = Response.error(
            400,
            "{\"detail\":\"invalid credentials\"}".toResponseBody(null)
        )
        val repo = buildRepo(storage, api)

        val result = repo.login("bob", "wrong")

        assertTrue(result.isFailure)
        assertEquals("账号或密码错误", result.exceptionOrNull()?.message)
    }

    @Test
    fun loginSurfacesUnmappedServerDetailAsIs() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        api.loginResult = Response.error(
            500,
            "{\"detail\":\"internal server error\"}".toResponseBody(null)
        )
        val repo = buildRepo(storage, api)

        val result = repo.login("bob", "secret1")

        assertTrue(result.isFailure)
        assertEquals("internal server error", result.exceptionOrNull()?.message)
    }

    @Test
    fun loginWithoutDetailFallsBackToStatusCode() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        api.loginResult = Response.error(500, "".toResponseBody(null))
        val repo = buildRepo(storage, api)

        val result = repo.login("bob", "secret1")

        assertTrue(result.isFailure)
        assertEquals("登录失败 (HTTP 500)", result.exceptionOrNull()?.message)
    }

    @Test
    fun registerMapsInvalidRequestDetail() = runTest {
        val storage = FakeAuthStorage()
        val api = FakeServerApiService()
        api.registerResult = Response.error(
            400,
            "{\"detail\":\"invalid request\"}".toResponseBody(null)
        )
        val repo = buildRepo(storage, api)

        val result = repo.register("carol", "secret1")

        assertTrue(result.isFailure)
        assertEquals("请求参数错误", result.exceptionOrNull()?.message)
    }

    private fun loggedInStorage() =
        FakeAuthStorage(initialToken = "token", initialUser = UserInfo("1", "bob", "Bob"))

    @Test
    fun updateProfileSuccessCallsServerAndWritesBack() = runTest {
        val storage = loggedInStorage()
        val api = FakeServerApiService()
        api.updateProfileResult = Response.success(UserDto("1", "bob", "NewName", "/uploads/avatars/1.jpg"))
        val repo = buildRepo(storage, api)

        val result = repo.updateProfile("NewName")

        assertTrue(result.isSuccess)
        assertEquals(1, api.updateProfileCalls)
        assertEquals("NewName", api.updateProfileRequest?.nickname)
        val user = (storage.authStateFlow.value as AuthState.LoggedIn).user
        assertEquals("NewName", user.nickname)
        assertEquals("/uploads/avatars/1.jpg", user.avatarUrl)
    }

    @Test
    fun updateProfileBlankNicknameRejectedLocally() = runTest {
        val storage = loggedInStorage()
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.updateProfile("   ")

        assertTrue(result.isFailure)
        assertEquals("昵称不能为空", result.exceptionOrNull()?.message)
        assertEquals(0, api.updateProfileCalls)
    }

    @Test
    fun updateProfileTooLongNicknameRejectedLocally() = runTest {
        val storage = loggedInStorage()
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.updateProfile("x".repeat(21))

        assertTrue(result.isFailure)
        assertEquals("昵称最长 20 字", result.exceptionOrNull()?.message)
        assertEquals(0, api.updateProfileCalls)
    }

    @Test
    fun updateProfileNetworkErrorKeepsLocalState() = runTest {
        val storage = loggedInStorage()
        val api = FakeServerApiService()
        api.updateProfileError = IOException("timeout")
        val repo = buildRepo(storage, api)

        val result = repo.updateProfile("NewName")

        assertTrue(result.isFailure)
        val user = (storage.authStateFlow.value as AuthState.LoggedIn).user
        assertEquals("Bob", user.nickname)
    }

    @Test
    fun updateProfileMapsUnauthorizedDetail() = runTest {
        val storage = loggedInStorage()
        val api = FakeServerApiService()
        api.updateProfileResult = Response.error(401, "{\"detail\":\"unauthorized\"}".toResponseBody(null))
        val repo = buildRepo(storage, api)

        val result = repo.updateProfile("NewName")

        assertTrue(result.isFailure)
        assertEquals("登录已失效，请重新登录", result.exceptionOrNull()?.message)
    }

    @Test
    fun updateProfileAdminUpdatesLocallyWithoutServer() = runTest {
        val storage = FakeAuthStorage(
            initialToken = "t",
            initialUser = UserInfo("admin", "admin", "管理员")
        )
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.updateProfile("超级管理员")

        assertTrue(result.isSuccess)
        assertEquals(0, api.updateProfileCalls)
        val user = (storage.authStateFlow.value as AuthState.LoggedIn).user
        assertEquals("超级管理员", user.nickname)
    }

    @Test
    fun changePasswordSuccessCallsServer() = runTest {
        val storage = loggedInStorage()
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.changePassword("secret1", "newsecret")

        assertTrue(result.isSuccess)
        assertEquals(1, api.changePasswordCalls)
        assertEquals("secret1", api.changePasswordRequest?.oldPassword)
        assertEquals("newsecret", api.changePasswordRequest?.newPassword)
    }

    @Test
    fun changePasswordShortNewPasswordRejectedLocally() = runTest {
        val storage = loggedInStorage()
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.changePassword("secret1", "123")

        assertTrue(result.isFailure)
        assertEquals("新密码至少 6 位", result.exceptionOrNull()?.message)
        assertEquals(0, api.changePasswordCalls)
    }

    @Test
    fun changePasswordWrongOldPasswordMappedMessage() = runTest {
        val storage = loggedInStorage()
        val api = FakeServerApiService()
        api.changePasswordResult = Response.error(
            401,
            "{\"detail\":\"old password incorrect\"}".toResponseBody(null)
        )
        val repo = buildRepo(storage, api)

        val result = repo.changePassword("wrong", "newsecret")

        assertTrue(result.isFailure)
        assertEquals("旧密码不正确", result.exceptionOrNull()?.message)
    }

    @Test
    fun changePasswordAdminRejected() = runTest {
        val storage = FakeAuthStorage(
            initialToken = "t",
            initialUser = UserInfo("admin", "admin", "管理员")
        )
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.changePassword("admin", "newsecret")

        assertTrue(result.isFailure)
        assertEquals("超级账号不支持修改密码", result.exceptionOrNull()?.message)
        assertEquals(0, api.changePasswordCalls)
    }

    @Test
    fun uploadAvatarSuccessWritesBackAvatarUrl() = runTest {
        val storage = loggedInStorage()
        val api = FakeServerApiService()
        api.uploadAvatarResult = Response.success(AvatarResponseDto("/uploads/avatars/1.jpg"))
        val repo = buildRepo(storage, api)

        val result = repo.uploadAvatar("base64-data")

        assertTrue(result.isSuccess)
        assertEquals(1, api.uploadAvatarCalls)
        assertEquals("base64-data", api.uploadAvatarRequest?.avatarBase64)
        val user = (storage.authStateFlow.value as AuthState.LoggedIn).user
        assertEquals("/uploads/avatars/1.jpg", user.avatarUrl)
        assertEquals("Bob", user.nickname)
    }

    @Test
    fun uploadAvatarNetworkErrorKeepsOriginalAvatar() = runTest {
        val storage = loggedInStorage()
        val api = FakeServerApiService()
        api.uploadAvatarError = IOException("timeout")
        val repo = buildRepo(storage, api)

        val result = repo.uploadAvatar("base64-data")

        assertTrue(result.isFailure)
        val user = (storage.authStateFlow.value as AuthState.LoggedIn).user
        assertNull(user.avatarUrl)
    }

    @Test
    fun uploadAvatarAdminRejected() = runTest {
        val storage = FakeAuthStorage(
            initialToken = "t",
            initialUser = UserInfo("admin", "admin", "管理员")
        )
        val api = FakeServerApiService()
        val repo = buildRepo(storage, api)

        val result = repo.uploadAvatar("base64-data")

        assertTrue(result.isFailure)
        assertEquals("超级账号不支持设置头像", result.exceptionOrNull()?.message)
        assertEquals(0, api.uploadAvatarCalls)
    }
}
