package ai.pleosapp.drive.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 결과 QR 웹 주소 확인 (PLEOS-64) */
class WebBaseUrlTest {

    @Test fun acceptsLaptopAndDeployedAddresses() {
        assertEquals("http://192.168.0.12:5173/", WebBaseUrl.parse("http://192.168.0.12:5173/"))
        assertEquals("http://192.168.0.12:5173", WebBaseUrl.parse("  http://192.168.0.12:5173  "))
        assertEquals("https://drive-run.example.com/app/", WebBaseUrl.parse("https://drive-run.example.com/app/"))
    }

    @Test fun rejectsMissingOrWrongAddresses() {
        assertNull(WebBaseUrl.parse(null))
        assertNull(WebBaseUrl.parse(""))
        assertNull(WebBaseUrl.parse("   "))
        // 주소 앞부분(http://)이 없음
        assertNull(WebBaseUrl.parse("192.168.0.12:5173"))
        // 폰 웹이 ?tripId= 를 붙이므로 기본 주소에 쿼리·#은 넣지 않는다
        assertNull(WebBaseUrl.parse("http://192.168.0.12:5173/?tripId=abc"))
        assertNull(WebBaseUrl.parse("http://192.168.0.12:5173/#ranking"))
        assertNull(WebBaseUrl.parse("ftp://192.168.0.12/"))
    }
}
