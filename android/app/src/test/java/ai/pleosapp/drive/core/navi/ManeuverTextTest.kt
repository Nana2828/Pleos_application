package ai.pleosapp.drive.core.navi

import ai.pleosapp.drive.core.drive.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManeuverTextTest {

    private fun step(type: String, modifier: String = "", exit: Int? = null, name: String = "소래로") =
        RouteStep(123.0, type, modifier, name, exit, 100.0, 10.0, GeoPoint(37.0, 127.0))

    private fun of(type: String, modifier: String = "", exit: Int? = null) = ManeuverText.of(step(type, modifier, exit))

    @Test fun turns() {
        assertEquals(ManeuverKind.LEFT to "좌회전", of("turn", "left")!!.let { it.kind to it.text })
        assertEquals(ManeuverKind.RIGHT to "우회전", of("turn", "right")!!.let { it.kind to it.text })
        assertEquals(ManeuverKind.SHARP_RIGHT to "급우회전", of("turn", "sharp right")!!.let { it.kind to it.text })
        assertEquals(ManeuverKind.SLIGHT_LEFT to "왼쪽 방향", of("turn", "slight left")!!.let { it.kind to it.text })
        assertEquals(ManeuverKind.U_TURN to "유턴", of("continue", "uturn")!!.let { it.kind to it.text })
        assertEquals(ManeuverKind.LEFT, of("end of road", "left")!!.kind)
        assertEquals(ManeuverKind.SLIGHT_RIGHT, of("fork", "slight right")!!.kind)
    }

    @Test fun keepsRoadNameAndPosition() {
        val m = of("turn", "left")!!
        assertEquals("소래로", m.roadName)
        assertEquals(123.0, m.atM, 0.0)
    }

    @Test fun continueAlongBend() {
        assertEquals(ManeuverKind.SLIGHT_RIGHT to "오른쪽 방향", of("continue", "right")!!.let { it.kind to it.text })
        assertEquals(ManeuverKind.SLIGHT_LEFT, of("continue", "left")!!.kind)
        assertEquals(ManeuverKind.SHARP_LEFT, of("continue", "sharp left")!!.kind)
    }

    @Test fun notAnnounced() {
        assertNull(of("depart", "left"))
        assertNull(of("new name", "straight"))
        assertNull(of("continue", "straight"))
        assertNull(of("turn", "straight"))
        assertNull(of("notification", "right"))
        assertNull(of("exit roundabout", "right"))
        assertNull(of("something new", ""))
    }

    @Test fun arriveAndRoundabouts() {
        assertEquals(ManeuverKind.ARRIVE to "목적지 도착", of("arrive")!!.let { it.kind to it.text })
        assertEquals("회전교차로 2번째 출구", of("roundabout", "right", exit = 2)!!.text)
        assertEquals("회전교차로", of("rotary", "")!!.text)
        assertEquals(ManeuverKind.ROUNDABOUT to "회전교차로 직진", of("roundabout turn", "straight", exit = 1)!!.let { it.kind to it.text })
        // 갈림길·T자에서는 직진도 알린다
        assertEquals(ManeuverKind.STRAIGHT, of("fork", "straight")!!.kind)
        assertEquals(ManeuverKind.STRAIGHT, of("end of road", "")!!.kind)
    }

    @Test fun everyStepInRealAssetIsHandled() {
        val route = DemoRouteCodec.decode(DemoAssets.text("bucheon-cat-91min.route.json"))
        for (section in listOf(route.approach, route.course)) {
            val announced = section.steps.mapNotNull(ManeuverText::of)
            // 출발 안내는 빠지고 도착은 남는다
            assertEquals(ManeuverKind.ARRIVE, announced.last().kind)
            assertEquals(section.steps.size - section.steps.count { ManeuverText.of(it) == null }, announced.size)
            announced.forEach { assert(it.text.isNotBlank()) }
        }
        assertEquals(3, route.course.steps.mapNotNull(ManeuverText::of).count { it.kind == ManeuverKind.U_TURN })
    }
}
