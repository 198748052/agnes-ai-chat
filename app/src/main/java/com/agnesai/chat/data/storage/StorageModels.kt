package com.agnesai.chat.data.storage

/** 存储空间页展示的单个内容类型分类。 */
data class StorageCategory(
    val type: String,
    val title: String,
    val sessionCount: Long,
    val messageCount: Long,
    val sizeBytes: Long
)

/** 存储空间页的完整统计结果。 */
data class StorageSummary(
    val totalBytes: Long,
    val dbBytes: Long,
    val cacheBytes: Long,
    val categories: List<StorageCategory>
)

/** 内容类型常量（与 SessionType 对应，另有 cache / all）。 */
object StorageType {
    const val CHAT = "chat"
    const val IMAGE = "image"
    const val VIDEO = "video"
    const val CACHE = "cache"
    const val ALL = "all"
}

/**
 * 按消息占比估算各内容类型在数据库中的占用大小。
 *
 * 规则：
 * - 单个类型大小 = dbBytes * (该类型消息数 / 总消息数)，向下取整；
 * - 总消息数为 0 时所有类型估算为 0；
 * - 取整导致的差值并入最后一个非零类型，保证各类估算之和等于 dbBytes。
 *
 * @param dbBytes 数据库文件总大小
 * @param messageCounts 各类型消息数（key 为 chat/image/video）
 * @return 各类型估算大小（key 与入参一致）
 */
fun estimateMessageSizes(
    dbBytes: Long,
    messageCounts: Map<String, Long>
): Map<String, Long> {
    val total = messageCounts.values.sum()
    if (total <= 0L || dbBytes <= 0L) {
        return messageCounts.mapValues { 0L }
    }
    val sizes = messageCounts.mapValues { (_, count) ->
        if (count <= 0L) 0L else dbBytes * count / total
    }.toMutableMap()
    val sum = sizes.values.sum()
    val diff = dbBytes - sum
    if (diff != 0L) {
        val lastNonZero = sizes.entries.lastOrNull { it.value > 0L }?.key
        if (lastNonZero != null) {
            sizes[lastNonZero] = sizes.getValue(lastNonZero) + diff
        }
    }
    return sizes
}

/** 将字节数格式化为人类可读字符串（B / KB / MB / GB）。 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
