package ai.pleosapp.drive.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeUiStatesTest {

    @Test
    fun selectedCourseIsHeart() {
        assertEquals("하트 코스", FakeUiStates.courses.selectedCourse?.name)
    }

    @Test
    fun courseShapesAreClosed() {
        for (course in FakeUiStates.courses.courses) {
            assertTrue("${course.name} 점이 너무 적음", course.shape.size >= 4)
            assertEquals("${course.name} 모양이 닫혀 있지 않음", course.shape.first(), course.shape.last())
        }
    }

    @Test
    fun heartStartsAtBottomTip() {
        val start = FakeUiStates.heartShape.first()
        val lowest = FakeUiStates.heartShape.minOf { it.lat }
        assertEquals(lowest, start.lat, 1e-9)
    }

    @Test
    fun resultMatchesMockup() {
        val r = FakeUiStates.result
        assertEquals(5_050L, r.durationSec)
        assertEquals(r.durationSec * 1000, r.endedAtEpochMs - r.startedAtEpochMs)
        assertNotNull(r.resultUrl)
        assertEquals(UploadStatus.FAILED, FakeUiStates.resultUploadFailed.upload)
    }
}
