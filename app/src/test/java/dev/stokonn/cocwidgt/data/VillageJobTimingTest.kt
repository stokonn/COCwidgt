package dev.stokonn.cocwidgt.data

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VillageJobTimingTest {

    private val now = 1_700_000_000_000L
    private val halfHour = TimeUnit.MINUTES.toMillis(30)

    /** Job whose absolute deadline is [finish]; started one half-hour window earlier. */
    private fun job(
        finish: Long,
        startedAt: Long = finish - halfHour,
        goblin: Boolean? = null,
        dataId: Long = 1L,
    ): VillageJob = VillageJob(
        id = "1",
        name = "Builder",
        section = "buildings",
        level = 2,
        startedAt = startedAt,
        remainingAtExportMs = finish - startedAt,
        goblin = goblin,
        dataId = dataId,
    )

    @Test
    fun countdownIsDerivedFromAbsoluteFinishTimestamp() {
        // Same export window observed at two different moments: remaining time
        // shrinks with wall-clock time and never depends on when it was parsed.
        val finish = now + halfHour
        val j = job(finish)
        assertEquals(halfHour, j.remainingMs(now))
        assertEquals(halfHour - 60_000, j.remainingMs(now + 60_000))

        // An earlier absolute deadline (older export) is already over now.
        val stale = job(now - TimeUnit.MINUTES.toMillis(30))
        assertEquals(0L, stale.remainingMs(now))
        assertEquals("Ready", stale.countdown(now))
        assertTrue(stale.isComplete(now))
    }

    @Test
    fun countdownFormatsMatchWidgetRendering() {
        assertEquals("2d 1h 0m", job(now + TimeUnit.HOURS.toMillis(49)).countdown(now))
        assertEquals("1h 5m", job(now + TimeUnit.MINUTES.toMillis(65)).countdown(now))
        assertEquals("12m", job(now + TimeUnit.MINUTES.toMillis(12)).countdown(now))
        assertEquals("Ready", job(now - 60_000).countdown(now))
    }

    @Test
    fun progressIsFractionBetweenFirstObservationAndFinish() {
        val start = now - halfHour
        val finish = now + halfHour
        val j = job(finish, startedAt = start)
        assertEquals(0.5, j.progress(now)!!.toDouble(), 0.001)
        assertEquals(0.25, j.progress(start + (finish - start) / 4)!!.toDouble(), 0.001)
        assertEquals(0.0, j.progress(start)!!.toDouble(), 0.001)
        assertEquals(1.0, j.progress(finish)!!.toDouble(), 0.001)
        // Clamped after the deadline passes (never over 100 %).
        assertEquals(1.0, j.progress(finish + 10 * halfHour)!!.toDouble(), 0.001)
        // Before the first observation there is no completed fraction yet.
        assertEquals(0.0, j.progress(start - 60_000)!!.toDouble(), 0.001)
    }

    @Test
    fun invalidTimestampsHaveNoProgress() {
        assertNull(job(now + halfHour, startedAt = 0L).progress(now)) // missing start
        assertNull(job(now, startedAt = now).progress(now)) // degenerate window
        assertNull(job(now, startedAt = now + halfHour).progress(now)) // finish before start
    }

    @Test
    fun completedUpgradesAreDetectedFromDeadline() {
        assertTrue(job(now - 1_000).isComplete(now))
        assertFalse(job(now + 1_000).isComplete(now))
        assertEquals(0L, job(now - 1_000).remainingMs(now))
    }

    @Test
    fun staleExportTimestampsStillProduceDeterministicState() {
        // An export from before the deadline: complete now, progress full.
        val old = job(now - TimeUnit.MINUTES.toMillis(5), startedAt = now - halfHour)
        assertTrue(old.isComplete(now))
        assertEquals(0L, old.remainingMs(now))
        assertEquals(1.0, old.progress(now)!!.toDouble(), 0.001)

        // Ancient timestamps never produce negative progress or overflow.
        val ancient = job(1_000L, startedAt = 999L)
        assertEquals(1.0, ancient.progress(now)!!.toDouble(), 0.001)
        assertTrue(ancient.isComplete(now))
    }

    @Test
    fun goblinFlagIsCarriedOnTimingWindows() {
        assertEquals(true, job(now + halfHour, goblin = true).goblin)
        assertEquals(false, job(now + halfHour, goblin = false).goblin)
        assertNull(job(now + halfHour).goblin)
    }
}
