package ai.pleosapp.drive.ui.result

import ai.pleosapp.drive.ui.state.FakeUiStates
import ai.pleosapp.drive.ui.state.MapPoint
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultLinkTest {
    private val state = FakeUiStates.result

    private fun payloadOf(url: String): JsonObject {
        val d = url.substringAfter("&d=")
        return Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(d), Charsets.UTF_8)).jsonObject
    }

    @Test
    fun 주소는_기본주소에_tripId와_d를_붙인다() {
        val url = ResultLink.build("https://drive.example/", state)
        assertTrue(url.startsWith("https://drive.example/?tripId=r${state.startedAtEpochMs}&d="))
        // 웹이 허용하는 주행 ID 모양
        assertTrue(Regex("^[A-Za-z0-9_-]{1,64}$").matches(ResultLink.tripId(state)))
        // 이미 ?가 있으면 &로 잇는다
        assertTrue(ResultLink.build("https://drive.example/?x=1", state).contains("?x=1&tripId="))
    }

    @Test
    fun d에는_주행_요약이_들어있다() {
        val p = payloadOf(ResultLink.build("https://drive.example/", state))
        assertEquals(1, p["v"]!!.jsonPrimitive.content.toInt())
        assertEquals(ResultLink.tripId(state), p["id"]!!.jsonPrimitive.content)
        assertEquals(state.courseName, p["course"]!!.jsonPrimitive.content)
        assertEquals("2026-11-05T10:12:00", p["start"]!!.jsonPrimitive.content)
        assertEquals("2026-11-05T11:36:10", p["end"]!!.jsonPrimitive.content)
        assertEquals(42.3, p["km"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(5050L, p["sec"]!!.jsonPrimitive.content.toLong())
        assertEquals(12.8, p["eff"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("l", p["unit"]!!.jsonPrimitive.content)
    }

    @Test
    fun 길은_줄이되_처음과_끝은_지키고_되돌릴_수_있다() {
        val dense = List(2000) { MapPoint(37.4 + it * 0.0001, 126.7 + it * 0.00005) }
        val small = ResultLink.shrink(dense, ResultLink.MAX_POINTS)
        assertEquals(ResultLink.MAX_POINTS, small.size)
        assertEquals(dense.first(), small.first())
        assertEquals(dense.last(), small.last())

        val back = ResultLink.decodePolyline(ResultLink.encodePolyline(small))
        assertEquals(small.size, back.size)
        small.zip(back).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-5)
            assertEquals(a.lng, b.lng, 1e-5)
        }
    }

    @Test
    fun 폴리라인은_구글_예시와_같다() {
        // https://developers.google.com/maps/documentation/utilities/polylinealgorithm
        val pts = listOf(MapPoint(38.5, -120.2), MapPoint(40.7, -120.95), MapPoint(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", ResultLink.encodePolyline(pts))
    }

    @Test
    fun QR에_담기는_길이는_실제_시연_크기에서_QR_한도_안이다() {
        val url = ResultLink.build("https://drive.example/", state)
        assertTrue("주소가 너무 길어요: ${url.length}", url.length < 2000)
    }
}
