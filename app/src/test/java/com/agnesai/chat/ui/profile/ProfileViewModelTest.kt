package com.agnesai.chat.ui.profile

import com.agnesai.chat.data.stats.PeriodCounts
import com.agnesai.chat.data.stats.StatsResult
import com.agnesai.chat.data.stats.StatsSource
import com.agnesai.chat.data.storage.StorageCategory
import com.agnesai.chat.data.storage.StorageSummary
import com.agnesai.chat.data.storage.StorageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stats(
        source: StatsSource = StatsSource.SERVER,
        imageTotal: Int = 5,
        videoTotal: Int = 3,
        todayImage: Int = 1,
        todayVideo: Int = 2
    ) = StatsResult(
        source = source,
        image = PeriodCounts(today = todayImage, week = 2, month = 4, total = imageTotal),
        video = PeriodCounts(today = todayVideo, week = 1, month = 2, total = videoTotal)
    )

    private fun storage(bytes: Long = 2048L) = StorageSummary(
        totalBytes = bytes,
        dbBytes = bytes,
        cacheBytes = 0L,
        categories = listOf(
            StorageCategory(StorageType.IMAGE, "图片生成", 1, 1, bytes)
        )
    )

    @Test
    fun `init loads stats and storage and disables loading`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = ProfileViewModel(
            loadStats = { stats() },
            loadStorage = { storage() }
        )

        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertNull(vm.uiState.value.error)
        assertEquals(5, vm.uiState.value.image.total)
        assertEquals(3, vm.uiState.value.video.total)
        assertEquals(StatsSource.SERVER, vm.uiState.value.source)
        assertNotNull(vm.uiState.value.storage)
        assertEquals(2048L, vm.uiState.value.storage?.totalBytes)
    }

    @Test
    fun `stats failure keeps empty data and sets error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = ProfileViewModel(
            loadStats = { null },
            loadStorage = { storage() }
        )

        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertEquals("概览加载失败", vm.uiState.value.error)
        assertEquals(0, vm.uiState.value.image.total)
        assertEquals(0, vm.uiState.value.video.total)
        assertNull(vm.uiState.value.storage)
    }

    @Test
    fun `stats exception treated as failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = ProfileViewModel(
            loadStats = { throw RuntimeException("boom") },
            loadStorage = { storage() }
        )

        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertEquals("概览加载失败", vm.uiState.value.error)
    }

    @Test
    fun `storage failure does not fail the overview`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = ProfileViewModel(
            loadStats = { stats(imageTotal = 2, videoTotal = 1) },
            loadStorage = { throw RuntimeException("no storage") }
        )

        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertNull(vm.uiState.value.error)
        assertEquals(2, vm.uiState.value.image.total)
        assertNull(vm.uiState.value.storage)
    }

    @Test
    fun `load refreshes values`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var imageTotal = 1
        val vm = ProfileViewModel(
            loadStats = { stats(imageTotal = imageTotal, videoTotal = 1) },
            loadStorage = { storage() }
        )

        testScheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.image.total)

        imageTotal = 9
        vm.load()
        testScheduler.advanceUntilIdle()

        assertEquals(9, vm.uiState.value.image.total)
        assertTrue(vm.uiState.value.loading.not())
    }

    @Test
    fun `offline source reflected in state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = ProfileViewModel(
            loadStats = { stats(source = StatsSource.LOCAL) },
            loadStorage = { null }
        )

        testScheduler.advanceUntilIdle()

        assertEquals(StatsSource.LOCAL, vm.uiState.value.source)
        assertNull(vm.uiState.value.storage)
    }
}
