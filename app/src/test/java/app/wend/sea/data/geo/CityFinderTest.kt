package app.wend.sea.data.geo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

/** 실제 도시 데이터(design/geo/cities.tsv.gz)로 찾기와 이름 고르기를 확인한다 */
class CityFinderTest {

    private fun nearest(lat: Double, lon: Double): CityEntity? =
        runBlocking { CityFinder.nearest(lat, lon) { cells -> cells.flatMap { byCell[it].orEmpty() } } }?.first

    private fun name(lat: Double, lon: Double, tag: String) = nearest(lat, lon)?.localName(Locale.forLanguageTag(tag))

    /** CityFinder.placeName 과 같은 순서 */
    private fun placeName(lat: Double, lon: Double, tag: String): String? = runBlocking {
        val locale = Locale.forLanguageTag(tag)
        val fetch: suspend (List<Int>) -> List<CityEntity> = { cells -> cells.flatMap { byCell[it].orEmpty() } }
        (CityFinder.nearest(lat, lon, accept = { it.localNameOrNull(locale) != null }, fetch = fetch) ?: CityFinder.nearest(lat, lon, fetch = fetch))
            ?.first?.localName(locale)
    }

    @Test
    fun kyotoStation() {
        assertEquals("교토", name(34.985, 135.758, "ko-KR"))
        assertEquals("京都", name(34.985, 135.758, "ja-JP"))
        assertEquals("Kyoto", name(34.985, 135.758, "en-US"))
    }

    @Test
    fun daNangInChinese() {
        // 다낭 시 행 근처. (16.054, 108.202)에서는 구 단위 행 Thanh Khê(清溪)가 더 가깝다
        assertEquals("岘港", name(16.068, 108.221, "zh-CN"))
        assertEquals("峴港", name(16.068, 108.221, "zh-TW"))
        assertEquals("峴港", name(16.068, 108.221, "zh-Hant"))
        assertEquals("峴港", name(16.068, 108.221, "zh-Hans-HK"))
        assertEquals("岘港", name(16.068, 108.221, "zh-SG"))
    }

    @Test
    fun placePrefersCityWithLocalName() {
        // 고비 사막: 가장 가까운 Tsoohor 는 한국어 이름이 없어 26km 떨어진 달란자드가드
        assertEquals("Tsoohor", name(43.5, 104.1, "ko-KR"))
        assertEquals("달란자드가드", placeName(43.5, 104.1, "ko-KR"))
        // 50km 안에 한국어 이름 있는 곳이 없으면 가장 가까운 곳 영어 이름
        assertEquals("Hwasŏng", placeName(41.2556, 129.4928, "ko-KR"))
        // 가까운 곳에 한국어 이름이 있으면 그대로
        assertEquals("교토", placeName(34.985, 135.758, "ko-KR"))
    }

    @Test
    fun middleOfPacific() {
        assertNull(nearest(0.0, -160.0))
    }

    @Test
    fun emptyLanguageFallsBackToEnglish() {
        // Yvetot(프랑스): ko · ja · zh 모두 비어 있음
        assertEquals("Yvetot", name(49.6174, 0.7581, "ko-KR"))
        assertEquals("Yvetot", name(49.6174, 0.7581, "zh-TW"))
        // 그 밖의 언어는 영어
        assertEquals("Kyoto", name(34.985, 135.758, "fr-FR"))
    }

    @Test
    fun crossesAntimeridian() {
        // 경도 179.9°에서 50km 안을 보면 날짜변경선 건너편(-180° 칸)도 같이 본다
        val cells = CityGrid.cellsAround(-16.8, 179.9, 50.0)
        assertTrue(CityGrid.cell(-168_000, 1_799_000) in cells)
        assertTrue(CityGrid.cell(-168_000, -1_799_000) in cells)
    }

    @Test
    fun cellMatchesBuildScript() {
        // tools/geo/build_cities_db.py 로 만든 cities.db 의 교토(lat 350211, lon 1357538)·다낭 칸 번호
        assertEquals(180631, CityGrid.cell(350211, 1357538))
        assertEquals(153216, CityGrid.cell(160678, 1082208))
    }

    @Test
    fun countryNameFromLocale() {
        val city = nearest(34.985, 135.758)!!.toCity(Locale.KOREA, 1.0)
        assertEquals("일본", city.countryName)
        assertEquals("JP", city.countryCode)
    }

    companion object {
        private val byCell: Map<Int, List<CityEntity>> by lazy {
            val file = listOf("../design/geo/cities.tsv.gz", "design/geo/cities.tsv.gz").map(::File).first { it.exists() }
            GZIPInputStream(file.inputStream()).bufferedReader().useLines { lines ->
                lines.drop(1).map { line ->
                    val c = line.split('\t')
                    val lat = (c[1].toDouble() * 1e4).roundToInt()
                    val lon = (c[2].toDouble() * 1e4).roundToInt()
                    CityEntity(
                        id = c[0].toLong(), lat = lat, lon = lon, cell = CityGrid.cell(lat, lon), cc = c[3],
                        pop = c[5].toIntOrNull() ?: 0, en = c[6], ko = c[7].ifEmpty { null }, ja = c[8].ifEmpty { null },
                        zhHans = c[9].ifEmpty { null }, zhHant = c[10].ifEmpty { null },
                    )
                }.groupBy { it.cell }
            }
        }
    }
}
