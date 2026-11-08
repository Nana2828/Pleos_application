package ai.pleosapp.core

import ai.pleosapp.core.TestGeo.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test fun distanceMatchesKnownScale() {
        assertEquals(1000.0, distanceM(at(0.0, 0.0), at(0.0, 1000.0)), 2.0)
        assertEquals(1000.0, distanceM(at(0.0, 0.0), at(1000.0, 0.0)), 2.0)
    }

    @Test fun headingsFollowTravelDirection() {
        val eastThenNorth = listOf(at(0.0, 0.0), at(1000.0, 0.0), at(1000.0, 1000.0))
        val headings = headingsAlong(eastThenNorth)
        assertEquals(90.0, headings[0].toDouble(), 2.0)
        assertEquals(45.0, headings[1].toDouble(), 2.0)
        assertEquals(0.0, ((headings[2] + 180) % 360 - 180).toDouble(), 2.0)
    }

    @Test fun pointToLineUsesNearestSegment() {
        val line = listOf(at(0.0, 0.0), at(1000.0, 0.0), at(1000.0, 1000.0))
        assertEquals(50.0, pointToLineM(at(500.0, 50.0), line), 1.0)
        assertEquals(30.0, pointToLineM(at(1030.0, 500.0), line), 1.0)
    }

    @Test fun projectionGivesPositionAlongSegment() {
        val p = projectToSegment(at(250.0, 20.0), at(0.0, 0.0), at(1000.0, 0.0))
        assertEquals(0.25, p.t, 0.01)
        assertEquals(20.0, p.distanceM, 1.0)
        // 선분 밖이면 가까운 끝에 붙는다.
        assertEquals(0.0, projectToSegment(at(-100.0, 0.0), at(0.0, 0.0), at(1000.0, 0.0)).t, 1e-9)
    }

    @Test fun resampleKeepsEndpointsAndCount() {
        val line = listOf(at(0.0, 0.0), at(1000.0, 0.0), at(1000.0, 1000.0))
        val out = resample(line, 21)
        assertEquals(21, out.size)
        assertEquals(line.first(), out.first())
        assertEquals(line.last(), out.last())
        out.zipWithNext { a, b -> assertEquals(100.0, distanceM(a, b), 2.0) }
    }

    @Test fun thinKeepsSpacingAndEndpoints() {
        val dense = (0..100).map { at(it * 1.01, 0.0) } // 약 1m 간격 101점
        val out = thin(dense, 10.0)
        assertEquals(dense.first(), out.first())
        assertEquals(dense.last(), out.last())
        assertEquals(11, out.size)
        out.zipWithNext { a, b -> assertTrue(distanceM(a, b) >= 9.9) }
    }

    @Test fun thinLeavesShortLinesAlone() {
        val two = listOf(at(0.0, 0.0), at(1.0, 0.0))
        assertEquals(two, thin(two, 10.0))
    }
}
