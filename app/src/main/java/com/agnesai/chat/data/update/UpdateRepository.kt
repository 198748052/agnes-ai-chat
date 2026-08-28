package com.agnesai.chat.data.update

/**
 * 更新检查仓库接口。
 *
 * 后续对接服务器时在 [UpdateRepositoryImpl] 中注入
 * [com.agnesai.chat.data.network.ServerApiService] 并替换占位逻辑。
 */
interface UpdateRepository {

    /**
     * 检查是否有新版本。
     *
     * @param currentVersionCode 当前 App 的 versionCode（BuildConfig.VERSION_CODE）
     * @return 有可用更新时返回 [UpdateInfo]，无更新时返回 null
     */
    suspend fun checkForUpdate(currentVersionCode: Int): Result<UpdateInfo?>
}
