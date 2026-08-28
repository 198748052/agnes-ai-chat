package com.agnesai.chat.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.agnesai.chat.BuildConfig
import com.agnesai.chat.data.announcement.AnnouncementRepository
import com.agnesai.chat.data.announcement.AnnouncementRepositoryImpl
import com.agnesai.chat.data.auth.AuthRepository
import com.agnesai.chat.data.auth.AuthRepositoryImpl
import com.agnesai.chat.data.generation.GenerationRepository
import com.agnesai.chat.data.generation.GenerationRepositoryImpl
import com.agnesai.chat.data.local.AppDatabase
import com.agnesai.chat.data.local.SettingsDataStore
import com.agnesai.chat.data.network.AgnesApiService
import com.agnesai.chat.data.network.AgnesGenerationApiService
import com.agnesai.chat.data.network.API_BASE_URL
import com.agnesai.chat.data.network.AuthInterceptor
import com.agnesai.chat.data.network.ChatMessageDto
import com.agnesai.chat.data.network.ChatMessageDtoAdapter
import com.agnesai.chat.data.network.ServerApiService
import com.agnesai.chat.data.repository.ChatRepository
import com.agnesai.chat.data.repository.MessageImageStoreImpl
import com.agnesai.chat.data.stats.StatsRepository
import com.agnesai.chat.data.storage.StorageRepository
import com.agnesai.chat.data.update.UpdateRepository
import com.agnesai.chat.data.update.UpdateRepositoryImpl
import com.agnesai.chat.data.works.MyWorksRepository
import com.agnesai.chat.ui.announcement.AnnouncementViewModel
import com.agnesai.chat.ui.auth.AuthViewModel
import com.agnesai.chat.ui.chat.ChatViewModel
import com.agnesai.chat.ui.generation.GenerationViewModel
import com.agnesai.chat.ui.myworks.MyWorksViewModel
import com.agnesai.chat.ui.profile.ProfileEditViewModel
import com.agnesai.chat.ui.profile.ProfileViewModel
import com.agnesai.chat.ui.settings.SettingsViewModel
import com.agnesai.chat.ui.stats.StatsViewModel
import com.agnesai.chat.ui.storage.StorageViewModel
import com.agnesai.chat.ui.update.UpdateViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val settingsDataStore = SettingsDataStore(appContext)

    private val moshi = Moshi.Builder()
        .add(ChatMessageDto::class.java, ChatMessageDtoAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val agnesApiService: AgnesApiService =
        retrofit.create(AgnesApiService::class.java)

    // 图片/视频生成需要更长的读写超时（生成可能耗时数十秒到数分钟）
    private val generationOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(360, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .apply {
            // 仅在 debug 构建记录请求/响应体（含图片 base64 数据，release 禁用避免性能与数据泄露）
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                        redactHeader("Authorization")
                    }
                )
            }
        }
        .build()

    private val generationRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(API_BASE_URL)
        .client(generationOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val agnesGenerationApiService: AgnesGenerationApiService =
        generationRetrofit.create(AgnesGenerationApiService::class.java)

    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 业务服务器：独立 baseUrl + AuthInterceptor 附加 JWT，401 时清除本地登录态。
    private val serverOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(
            AuthInterceptor(
                tokenProvider = { runBlocking { settingsDataStore.getAuthToken() } },
                onUnauthorized = {
                    authScope.launch { settingsDataStore.clearAuth() }
                }
            )
        )
        .build()

    private val serverRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.SERVER_BASE_URL)
        .client(serverOkHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val serverApiService: ServerApiService =
        serverRetrofit.create(ServerApiService::class.java)

    private val database = AppDatabase.getInstance(appContext)

    private val chatRepository: ChatRepository by lazy {
        ChatRepository(
            apiService = agnesApiService,
            settingsDataStoreProvider = { settingsDataStore.getApiKey() to settingsDataStore.getSystemPrompt() },
            sessionDao = database.sessionDao(),
            messageDao = database.messageDao(),
            chatSettingsProvider = { settingsDataStore.getChatSettings() },
            imageStore = MessageImageStoreImpl(appContext)
        )
    }

    private val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(settingsDataStore, serverApiService)
    }

    private val announcementRepository: AnnouncementRepository by lazy {
        AnnouncementRepositoryImpl(serverApiService)
    }

    private val updateRepository: UpdateRepository by lazy {
        UpdateRepositoryImpl(serverApiService)
    }

    private val generationRepository: GenerationRepository by lazy {
        GenerationRepositoryImpl(
            apiService = agnesGenerationApiService,
            apiKeyProvider = { settingsDataStore.getApiKey() }
        )
    }

    private val storageRepository: StorageRepository by lazy {
        StorageRepository(appContext, database)
    }

    private val myWorksRepository: MyWorksRepository by lazy {
        MyWorksRepository(database.messageDao())
    }

    private val statsRepository: StatsRepository by lazy {
        StatsRepository(serverApiService, database.messageDao())
    }

    val chatViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { ChatViewModel(chatRepository) }
    }

    val settingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SettingsViewModel(settingsDataStore) }
    }

    val authViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { AuthViewModel(authRepository) }
    }

    val announcementViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { AnnouncementViewModel(announcementRepository) }
    }

    val updateViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { UpdateViewModel(updateRepository, BuildConfig.VERSION_CODE) }
    }

    val generationViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            GenerationViewModel(
                repository = generationRepository,
                chatRepository = chatRepository
            )
        }
    }

    val storageViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { StorageViewModel(storageRepository) }
    }

    val myWorksViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { MyWorksViewModel(myWorksRepository) }
    }

    val profileEditViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { ProfileEditViewModel(authRepository) }
    }

    val statsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { StatsViewModel(statsRepository, storageRepository) }
    }

    val profileViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ProfileViewModel(
                loadStats = { statsRepository.loadStats() },
                loadStorage = { storageRepository.getStorageSummary() }
            )
        }
    }
}
