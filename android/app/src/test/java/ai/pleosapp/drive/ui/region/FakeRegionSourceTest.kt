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
    fun 경기도를_검색하면_추천_코스_3개가_나온다() {
        val names = FakeRegionSource.search("경기도").map { it.name }
        assertEquals(listOf("A. 표범 코스", "B. 하트 코스", "C. 촛불 코스"), names)
    }

    @Test
    fun 경기만_입력해도_추천_코스가_나온다() {
        assertEquals(3, FakeRegionSource.search("경기").size)
    }

    @Test
    fun 경기도가_아닌_지역은_결과가_없다() {
        assertTrue(FakeRegionSource.search("서울").isEmpty())
        assertTrue(FakeRegionSource.search("수원").isEmpty())
    }
}
