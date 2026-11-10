package ai.pleosapp.drive.core.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayPlayerTest {
    private fun speedAt(t: Long) = DriveSignal.Speed(t, t / 1000.0)

    private val signals = (0..10).map { speedAt(it * 1000L) }

    @Test fun advanceUsesSpeedMultiplier() {
        val player = ReplayPlayer(signals)
        player.speed = 4.0
        val got = mutableListOf<DriveSignal>()
        player.advanceBy(500, got::add) // 0.5초 × 4 = 2초
        assertEquals(listOf(0L, 1000L, 2000L), got.map { it.timeMs })
        assertEquals(2000L, player.positionMs)
        player.advanceBy(1000, got::add) // + 4초 → 6초
        assertEquals(7, got.size)
        assertFalse(player.isFinished)
    }

    @Test fun finishesAtEndAndStopsSending() {
        val player = ReplayPlayer(signals)
        player.speed = 100.0
        val got = mutableListOf<DriveSignal>()
        player.advanceBy(1000, got::add)
        assertEquals(11, got.size)
        assertTrue(player.isFinished)
        assertEquals(0, player.advanceBy(1000, got::add))
        assertEquals(10_000L, player.positionMs)
    }

    @Test fun seekSendsSkippedSignalsInOrderAndNeverGoesBack() {
        val player = ReplayPlayer(signals.shuffled(java.util.Random(3)))
        val got = mutableListOf<Long>()
        player.seekTo(5_000) { got += it.timeMs }
        assertEquals((0..5).map { it * 1000L }, got)
        player.seekTo(1_000) { got += it.timeMs }
        assertEquals(6, got.size)
        assertEquals(5_000L, player.positionMs)
    }

    @Test fun seekUntilStopsRightAfterMatch() {
        val player = ReplayPlayer(signals)
        val got = mutableListOf<Long>()
        val found = player.seekUntil({ got += it.timeMs }) { it.timeMs == 3_000L }
        assertTrue(found)
        assertEquals(listOf(0L, 1000L, 2000L, 3000L), got)
        assertEquals(4000L, player.peek()?.timeMs)
    }

    @Test fun resetStartsOver() {
        val player = ReplayPlayer(signals)
        player.seekTo(10_000) {}
        player.reset()
        assertFalse(player.isFinished)
        assertEquals(0L, player.positionMs)
        var count = 0
        player.seekTo(10_000) { count++ }
        assertEquals(11, count)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroSpeed() {
        ReplayPlayer(signals).speed = 0.0
    }

    @Test fun demoAssetReplaysIntoOneFinishedDrive() {
        val text = TestGeo.resource("demo/bucheon-cat-91min.drive.jsonl")
        val replay = ContractCodec.decodeReplay(text)
        assertTrue(replay.synthetic)
        val player = ReplayPlayer(replay.signals)
        val session = DriveSession(DriveOrigin(DriveOrigin.Kind.REPLAY, replay.name, replay.synthetic))
        val events = mutableListOf<SessionEvent>()
        player.speed = 90.0
        while (!player.isFinished) player.advanceBy(100) { s -> session.handle(s)?.let(events::add) }
        assertTrue(events.first() is SessionEvent.Started)
        val record = (events.last() as SessionEvent.Finished).record
        // 가산 → 시작점 15.5km + 코스 50.5km (PLEOS-62)
        assertEquals(66.0, record.distanceM / 1000, 0.5)
        // 약 112분 주행을 90배로 재생하면 약 75초
        assertEquals(74.5, player.durationMs / 90.0 / 1000, 3.0)
    }
}
