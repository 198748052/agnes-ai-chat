package com.agnesai.chat.data.storage

import android.content.Context
import com.agnesai.chat.data.local.AppDatabase
import com.agnesai.chat.data.local.SessionType
import java.io.File

/** 统计 App 本地存储占用并执行清理。 */
class StorageRepository(
    private val context: Context,
    private val database: AppDatabase
) {

    private val sessionDao get() = database.sessionDao()
    private val messageDao get() = database.messageDao()

    /** 汇总本地存储占用：数据库文件 + 缓存目录 + 各内容类型数量与估算大小。 */
    suspend fun getStorageSummary(): StorageSummary {
        val dbBytes = dbFileSize()
        val cacheBytes = dirSize(context.cacheDir)
        val contentTypes = listOf(SessionType.CHAT, SessionType.IMAGE, SessionType.VIDEO)

        val sessionCounts = contentTypes.associateWith { sessionDao.countByType(it) }
        val messageCounts = contentTypes.associateWith { messageDao.countByType(it) }
        val sizeEstimates = estimateMessageSizes(dbBytes, messageCounts)

        val categories = buildList {
            contentTypes.forEach { type ->
                add(
                    StorageCategory(
                        type = type,
                        title = typeTitle(type),
                        sessionCount = sessionCounts.getValue(type),
                        messageCount = messageCounts.getValue(type),
                        sizeBytes = sizeEstimates.getValue(type)
                    )
                )
            }
            add(
                StorageCategory(
                    type = StorageType.CACHE,
                    title = "缓存",
                    sessionCount = 0,
                    messageCount = 0,
                    sizeBytes = cacheBytes
                )
            )
        }

        return StorageSummary(
            totalBytes = dbBytes + cacheBytes,
            dbBytes = dbBytes,
            cacheBytes = cacheBytes,
            categories = categories
        )
    }

    /** 删除指定内容类型的全部会话（消息级联删除），并回收数据库空间。 */
    suspend fun clearByType(type: String) {
        if (type in SessionType.ALL) {
            sessionDao.deleteByType(type)
            vacuum()
        }
    }

    /** 清空缓存目录下的文件与子目录。 */
    suspend fun clearCache() {
        clearDirContents(context.cacheDir)
    }

    /** 清空全部会话与消息，并清空缓存，回收数据库空间。 */
    suspend fun clearAll() {
        SessionType.ALL.forEach { sessionDao.deleteByType(it) }
        clearDirContents(context.cacheDir)
        vacuum()
    }

    /** 通过 VACUUM 回收删除数据后未释放的数据库空间。 */
    private fun vacuum() {
        runCatching {
            database.openHelper.writableDatabase.execSQL("VACUUM")
        }
    }

    private fun dbFileSize(): Long {
        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        if (!dbFile.exists()) return 0L
        var size = dbFile.length()
        for (ext in listOf("-wal", "-shm")) {
            val companion = File(dbFile.absolutePath + ext)
            if (companion.exists()) size += companion.length()
        }
        return size
    }

    private fun dirSize(dir: File): Long =
        if (!dir.exists()) 0L
        else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun clearDirContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                clearDirContents(f)
                f.delete()
            } else {
                f.delete()
            }
        }
    }

    private fun typeTitle(type: String): String = when (type) {
        SessionType.CHAT -> "文本聊天"
        SessionType.IMAGE -> "图片生成"
        SessionType.VIDEO -> "视频生成"
        else -> type
    }
}
