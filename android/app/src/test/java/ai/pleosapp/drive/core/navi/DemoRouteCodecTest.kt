package ai.pleosapp.drive.core.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoRouteCodecTest {

    @Test fun decodesRealAsset() {
        val route = DemoRouteCodec.decode(DemoAssets.text("demo-route.json"))
        assertEquals("bucheon-cat-91min", route.courseId)
        // 부천 근처, 위도·경도 순서가 바뀌지 않았는지
        assertEquals(37.452, route.origin.lat, 1e-6)
        assertEquals(126.758, route.origin.lon, 1e-6)
        for (section in listOf(route.approach, route.course)) {
            assertTrue(section.geometry.size > 10)
            assertTrue(section.geometry.all { it.lat in 37.3..37.6 && it.lon in 126.6..126.9 })
            assertTrue(section.steps.isNotEmpty())
            assertEquals("depart", section.steps.first().type)
            assertEquals("arrive", section.steps.last().type)
            // 마지막 안내 위치는 구간 길이와 같다
            assertEquals(section.distanceM, section.steps.last().atM, 1.0)
        }
        assertTrue(route.approach.distanceM in 2_500.0..4_000.0)
        assertTrue(route.course.distanceM in 48_000.0..53_000.0)
        assertEquals(3, route.course.steps.count { it.modifier == "uturn" })
    }

    @Test fun decodesMinimalJson() {
        val route = DemoRouteCodec.decode(minimal())
        assertEquals("c1", route.courseId)
        val step = route.course.steps[1]
        assertEquals("turn", step.type)
        assertEquals("left", step.modifier)
        assertEquals("가로", step.name)
        assertEquals(2, step.exit)
        assertEquals(37.001, step.location.lat, 1e-9)
        assertEquals(127.001, step.location.lon, 1e-9)
        assertEquals(0, route.approach.steps.size)
    }

    @Test fun rejectsBadInput() {
        assertThrows(IllegalArgumentException::class.java) { DemoRouteCodec.decode("not json") }
        assertThrows(IllegalArgumentException::class.java) { DemoRouteCodec.decode("[]") }
        assertThrows(IllegalArgumentException::class.java) { DemoRouteCodec.decode(minimal().replace("\"courseId\"", "\"id\"")) }
        // 위도 자리에 200 → 범위 오류
        assertThrows(IllegalArgumentException::class.java) {
            DemoRouteCodec.decode(minimal().replace("[127.0, 37.0]", "[37.0, 200.0]"))
        }
        // 점이 하나뿐인 경로
        assertThrows(IllegalArgumentException::class.java) {
            DemoRouteCodec.decode(minimal().replace("[[127.0, 37.0], [127.001, 37.001]]", "[[127.0, 37.0]]"))
        }
        // 다른 format
        assertThrows(IllegalArgumentException::class.java) {
            DemoRouteCodec.decode(minimal().replace("\"demo-route\"", "\"drive-replay\""))
        }
    }

    private fun minimal() = """
        {
          "format": "demo-route", "version": 1, "courseId": "c1",
          "origin": {"lat": 37.0, "lon": 127.0, "name": "현재 위치"},
          "approach": {"distanceM": 150, "durationSec": 20, "geometry": [[127.0, 37.0], [127.001, 37.001]], "steps": []},
          "course": {
            "distanceM": 150, "durationSec": 20,
            "geometry": [[127.0, 37.0], [127.001, 37.001]],
            "steps": [
              {"atM": 0, "type": "depart", "modifier": "", "name": "", "exit": null, "lengthM": 100, "durationSec": 10, "location": [127.0, 37.0]},
              {"atM": 100, "type": "turn", "modifier": "left", "name": "가로", "exit": 2, "lengthM": 50, "durationSec": 10, "location": [127.001, 37.001]}
            ]
          }
        }
    """.trimIndent()
}
