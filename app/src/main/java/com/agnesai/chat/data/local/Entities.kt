package com.agnesai.chat.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** 会话类型：chat（文本聊天）/ image（图片生成）/ video（视频生成） */
    val type: String = SessionType.CHAT,
    val createdAt: Long,
    val updatedAt: Long
)

object SessionType {
    const val CHAT = "chat"
    const val IMAGE = "image"
    const val VIDEO = "video"

    val ALL = setOf(CHAT, IMAGE, VIDEO)
}

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["role", "status"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val content: String,
    /** 生成参数 JSON（图片/视频生成消息专用，文本消息为 null） */
    val params: String? = null,
    /** 多模态图片相对路径 JSON 数组（存于 filesDir 下，文本消息为 null） */
    val imagePaths: String? = null,
    val timestamp: Long,
    val status: String
)

object MessageStatus {
    const val DONE = "done"
    const val ERROR = "error"
    const val STREAMING = "streaming"
    const val SENDING = "sending"
}

object Roles {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val SYSTEM = "system"
}
