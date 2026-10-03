package com.karthi.smartcleaner.work

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class ScanSchedulerTest {

    @Test
    fun `schedules later today when time has not passed`() {
        val now = LocalDateTime.of(2026, 1, 1, 8, 30)
        assertEquals(Duration.ofMinutes(90), ScanScheduler.delayUntilNext(now, 10, 0))
    }

    @Test
    fun `rolls over to tomorrow when time has passed or is now`() {
        val now = LocalDateTime.of(2026, 1, 1, 21, 0)
        assertEquals(Duration.ofHours(24), ScanScheduler.delayUntilNext(now, 21, 0))
        assertEquals(Duration.ofHours(23), ScanScheduler.delayUntilNext(now, 20, 0))
    }
}
