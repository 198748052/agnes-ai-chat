package com.agnesai.chat.data.update

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    /** true 表示强制更新，弹窗不可关闭 */
    val forceUpdate: Boolean,
    val updateLog: String,
    val downloadUrl: String
)
