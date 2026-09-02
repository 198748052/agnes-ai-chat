package com.agnesai.chat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.agnesai.chat.data.works.MyWorkRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE type = :type ORDER BY updatedAt DESC")
    fun observeSessionsByType(type: String): Flow<List<SessionEntity>>

    @Query("UPDATE sessions SET updatedAt = :updatedAt, title = :title WHERE id = :id")
    suspend fun update(id: Long, updatedAt: Long, title: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM sessions WHERE type = :type")
    suspend fun countByType(type: String): Long

    @Query("DELETE FROM sessions WHERE type = :type")
    suspend fun deleteByType(type: String): Int
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(sessionId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessages(sessionId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET content = :content, status = :status WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, status: String)

    @Query("UPDATE messages SET content = :content, params = :params, status = :status WHERE id = :id")
    suspend fun updateContentAndParams(id: Long, content: String, params: String?, status: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId AND role = 'user' AND status = 'done'")
    suspend fun countUserMessages(sessionId: Long): Int

    @Query(
        "SELECT COUNT(*) FROM messages WHERE sessionId IN (SELECT id FROM sessions WHERE type = :type)"
    )
    suspend fun countByType(type: String): Long

    /**
     * 查询全部已成功的生成作品（图片/视频）。
     *
     * 关联会话表取类型与标题，并关联该会话内时间上最近一条用户消息作为提示词。
     */
    @Query(
        """
        SELECT
            m.id,
            m.sessionId,
            m.content,
            m.params,
            m.timestamp,
            s.title AS sessionTitle,
            s.type AS sessionType,
            (
                SELECT u.content FROM messages u
                WHERE u.sessionId = m.sessionId AND u.role = 'user' AND u.status = 'done'
                ORDER BY u.timestamp DESC, u.id DESC
                LIMIT 1
            ) AS prompt
        FROM messages m
        INNER JOIN sessions s ON m.sessionId = s.id
        WHERE m.role = 'assistant' AND m.status = 'done'
          AND m.params IS NOT NULL
        ORDER BY m.timestamp DESC, m.id DESC
        """
    )
    fun observeCompletedWorks(): Flow<List<MyWorkRow>>
}
