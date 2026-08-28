package com.agnesai.chat.data.update

import com.agnesai.chat.data.network.ServerApiService
import java.io.IOException

class UpdateRepositoryImpl(
    private val serverApiService: ServerApiService
) : UpdateRepository {

    override suspend fun checkForUpdate(currentVersionCode: Int): Result<UpdateInfo?> = runCatching {
        val response = serverApiService.getAppVersion()
        if (!response.isSuccessful) {
            throw IOException("获取版本信息失败 (HTTP ${response.code()})")
        }
        val body = response.body() ?: return@runCatching null
        if (body.latestVersionCode <= currentVersionCode) {
            return@runCatching null
        }
        UpdateInfo(
            latestVersionCode = body.latestVersionCode,
            latestVersionName = body.latestVersionName,
            forceUpdate = body.forceUpdate,
            updateLog = body.updateLog,
            downloadUrl = body.downloadUrl
        )
    }
}
