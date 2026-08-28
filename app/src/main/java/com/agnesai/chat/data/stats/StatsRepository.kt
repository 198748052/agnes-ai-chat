package com.agnesai.chat.data.stats

import com.agnesai.chat.data.local.MessageDao
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.network.PeriodCountsDto
import com.agnesai.chat.data.network.ServerApiService
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** 单个类型的四时段生成计数。 */
data class PeriodCounts(
    val today: Int = 0,
    val week: Int = 0,
    val month: Int = 0,
    val total: Int = 0
)

/** 统计数据来源。 */
enum class StatsSource { SERVER, LOCAL }

/** 生成统计结果。 */
data class StatsResult(
    val source: StatsSource,
    val image: PeriodCounts,
    val video: PeriodCounts
)

/**
 * 生成统计仓库：优先请求后端聚合统计，后端不可用时回退到本地已完成作品聚合。
 */
class StatsRepository(
    private val serverApiService: ServerApiService,
    private val messageDao: MessageDao
) {

    suspend fun loadStats(
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now()
    ): StatsResult =
        runCatching { fetchServerStats() }.getOrNull() ?: computeLocalStats(zone, now)

    private suspend fun fetchServerStats(): StatsResult? {
        val response = serverApiService.getStats()
        if (!response.isSuccessful) return null
        val body = response.body() ?: return null
        return StatsResult(
            source = StatsSource.SERVER,
            image = body.image.toPeriodCounts(),
            video = body.video.toPeriodCounts()
        )
    }

    private suspend fun computeLocalStats(zone: ZoneId, now: Instant): StatsResult {
        val works = messageDao.observeCompletedWorks().first()
        return StatsResult(
            source = StatsSource.LOCAL,
            image = aggregateCounts(
                works.filter { it.sessionType == SessionType.IMAGE }.map { it.timestamp },
                zone,
                now
            ),
            video = aggregateCounts(
                works.filter { it.sessionType == SessionType.VIDEO }.map { it.timestamp },
                zone,
                now
            )
        )
    }
}

private fun PeriodCountsDto.toPeriodCounts(): PeriodCounts =
    PeriodCounts(today = today, week = week, month = month, total = total)

/**
 * 按本地时区聚合计数：今日（零点起）、本周（周一起）、本月（1 日起）、累计。
 * 时间戳为毫秒。`now` 可注入便于测试。纯函数，便于 JVM 单元测试。
 */
fun aggregateCounts(
    timestamps: List<Long>,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now()
): PeriodCounts {
    if (timestamps.isEmpty()) return PeriodCounts()
    val current = now.atZone(zone)
    val todayStart = current.toLocalDate().atStartOfDay(zone)
    val weekStart = current.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(zone)
    val monthStart = current.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)

    var today = 0
    var week = 0
    var month = 0
    for (ts in timestamps) {
        val dt = Instant.ofEpochMilli(ts).atZone(zone)
        if (!dt.isBefore(todayStart)) today++
        if (!dt.isBefore(weekStart)) week++
        if (!dt.isBefore(monthStart)) month++
    }
    return PeriodCounts(today = today, week = week, month = month, total = timestamps.size)
}
