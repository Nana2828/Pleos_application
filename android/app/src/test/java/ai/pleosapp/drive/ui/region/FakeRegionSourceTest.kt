package ai.pleosapp.drive.ui.region

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRegionSourceTest {
    @Test
    fun 빈_검색어는_결과가_없다() {
        assertTrue(FakeRegionSource.search("").isEmpty())
        assertTrue(FakeRegionSource.search("   ").isEmpty())
    }

    @Test
    fun 수원을_검색하면_시안과_같은_3개가_나온다() {
        val names = FakeRegionSource.search("수원").map { it.name }
        assertEquals(listOf("수원시", "수원역", "수원화성"), names)
    }

    @Test
    fun 없는_지역은_결과가_없다() {
        assertTrue(FakeRegionSource.search("없는동네").isEmpty())
    }

    @Test
    fun 결과는_최대_3개() {
        assertTrue(FakeRegionSource.search("시").size <= FakeRegionSource.MAX_RESULTS)
    }
}
