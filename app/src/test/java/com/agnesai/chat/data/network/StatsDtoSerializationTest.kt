package com.agnesai.chat.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsDtoSerializationTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(UserStatsDto::class.java)

    @Test
    fun `parses full stats json`() {
        val dto = adapter.fromJson(
            """{"image":{"today":2,"week":5,"month":9,"total":42},"video":{"today":1,"week":3,"month":4,"total":10}}"""
        )
        assertEquals(2, dto?.image?.today)
        assertEquals(42, dto?.image?.total)
        assertEquals(1, dto?.video?.today)
        assertEquals(10, dto?.video?.total)
    }

    @Test
    fun `parses partial period counts with defaults`() {
        val dto = adapter.fromJson(
            """{"image":{"today":3},"video":{"total":7}}"""
        )
        assertEquals(PeriodCountsDto(today = 3), dto?.image)
        assertEquals(PeriodCountsDto(total = 7), dto?.video)
    }

    @Test
    fun `parses empty stats object to defaults`() {
        val dto = adapter.fromJson("""{}""")
        assertEquals(PeriodCountsDto(), dto?.image)
        assertEquals(PeriodCountsDto(), dto?.video)
    }
}
