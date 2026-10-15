package app.wend.sea.data.geo

import app.wend.sea.data.analysis.TripDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/** 좌표에서 가장 가까운 도시. [name] 은 사용자 언어, 없으면 영어 */
data class City(
    val id: Long,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val distanceKm: Double,
    val population: Int,
)

/** 위도·경도를 0.5° 칸으로 나눈 번호. tools/geo/build_cities_db.py 의 cell() 과 같아야 한다 */
object CityGrid {
    const val CELL_DEG = .5
    private const val LAT_CELLS = 360
    private const val LON_CELLS = 720

    fun cell(latE4: Int, lonE4: Int): Int {
        val li = minOf(floorDiv(latE4 + 900_000, 5_000), LAT_CELLS - 1)
        val lo = floorDiv(lonE4 + 1_800_000, 5_000).mod(LON_CELLS)
        return li * LON_CELLS + lo
    }

    /** 중심에서 [radiusKm] 안을 모두 덮는 칸들(날짜변경선은 이어 붙임) */
    fun cellsAround(lat: Double, lon: Double, radiusKm: Double): List<Int> {
        val dLat = radiusKm / KM_PER_DEG
        val cosLat = cos(lat.coerceIn(-89.0, 89.0) * PI / 180)
        val dLon = radiusKm / (KM_PER_DEG * cosLat)
        val liFrom = floor((max(lat - dLat, -90.0) + 90) / CELL_DEG).toInt()
        val liTo = minOf(floor((minOf(lat + dLat, 90.0) + 90) / CELL_DEG).toInt(), LAT_CELLS - 1)
        val loRange = if (dLon >= 180) 0 until LON_CELLS else
            floor((lon - dLon + 180) / CELL_DEG).toInt()..floor((lon + dLon + 180) / CELL_DEG).toInt()
        return buildList {
            for (li in liFrom..liTo) for (lo in loRange) add(li * LON_CELLS + lo.mod(LON_CELLS))
        }.distinct()
    }

    /** 위도·경도 상자에 걸치는 칸들 */
    fun cellsInBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<Int> {
        val liFrom = floor((max(minLat, -90.0) + 90) / CELL_DEG).toInt()
        val liTo = minOf(floor((minOf(maxLat, 90.0) + 90) / CELL_DEG).toInt(), LAT_CELLS - 1)
        val loFrom = floor((minLon + 180) / CELL_DEG).toInt()
        val loTo = floor((maxLon + 180) / CELL_DEG).toInt()
        if (liTo < liFrom || loTo < loFrom || (liTo - liFrom + 1L) * (loTo - loFrom + 1L) > 5000) return emptyList()
        return buildList { for (li in liFrom..liTo) for (lo in loFrom..loTo) add(li * LON_CELLS + lo.mod(LON_CELLS)) }.distinct()
    }

    private fun floorDiv(a: Int, b: Int) = Math.floorDiv(a, b)

    private const val KM_PER_DEG = 111.2
}

/**
 * 오프라인 역지오코딩: 주변 칸의 도시만 가져와 하버사인 거리로 가장 가까운 곳을 고른다.
 * 가까운 범위부터 보고 없으면 넓히며, [MAX_KM] 안에 없으면(바다 한가운데 등) null.
 * 인터넷을 쓰지 않는다.
 */
@Singleton
class CityFinder @Inject constructor(private val dao: CityDao) {

    suspend fun nearestCity(lat: Double, lon: Double, locale: Locale): City? = withContext(Dispatchers.IO) {
        nearest(lat, lon) { dao.inCells(it) }?.let { (row, km) -> row.toCity(locale, km) }
    }


    /**
     * 범위 안의 도시를 인구 순으로 [limit] 개(지도에 마을 이름을 찍을 때). 범위가 너무 넓으면(칸 600개 넘게) 빈 목록 —
     * 그 축척에서는 기본 지도의 큰 도시로 충분하다.
     */
    suspend fun biggestIn(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, limit: Int): List<CityEntity> = withContext(Dispatchers.IO) {
        val cells = CityGrid.cellsInBox(minLat, maxLat, minLon, maxLon)
        if (cells.isEmpty() || cells.size > 600) return@withContext emptyList()
        cells.chunked(500).flatMap { dao.biggestInCells(it, limit) }
            .filter { it.lat / 1e4 in minLat..maxLat && it.lon / 1e4 in minLon..maxLon }
            .sortedByDescending { it.pop }
            .take(limit)
    }

    /** 저장해 둔 도시 id 들의 행 */
    suspend fun byIds(ids: Collection<Long>): Map<Long, CityEntity> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) emptyMap() else dao.byIds(ids.distinct()).associateBy { it.id }
    }

    companion object {
        const val MAX_KM = 50.0
        private val RADII_KM = doubleArrayOf(10.0, 25.0, MAX_KM)

        /** [fetch] 로 칸들의 도시를 받아 [accept] 를 만족하는 가장 가까운 곳과 거리(km) */
        suspend fun nearest(
            lat: Double,
            lon: Double,
            accept: (CityEntity) -> Boolean = { true },
            fetch: suspend (List<Int>) -> List<CityEntity>,
        ): Pair<CityEntity, Double>? {
            val seen = HashSet<Int>()
            var best: Pair<CityEntity, Double>? = null
            for (radius in RADII_KM) {
                val cells = CityGrid.cellsAround(lat, lon, radius).filter { seen.add(it) }
                if (cells.isNotEmpty()) {
                    for (row in fetch(cells)) {
                        if (!accept(row)) continue
                        val km = TripDetector.haversineKm(lat, lon, row.lat / 1e4, row.lon / 1e4)
                        if (best == null || km < best.second) best = row to km
                    }
                }
                // 이 반경 안의 도시는 모두 본 칸에 들어 있으므로, 반경 안에서 찾았으면 더 넓힐 필요 없다
                best?.let { if (it.second <= radius) return it }
            }
            return null
        }
    }
}

/** 사용자 언어의 이름. ko · ja · zh(번체: Hant 또는 TW·HK·MO) 외에는 영어, 비어 있어도 영어 */
fun CityEntity.localName(locale: Locale): String = localNameOrNull(locale) ?: en

/** 사용자 언어의 이름이 데이터에 있을 때만. ko · ja · zh 외의 언어는 영어 이름이 곧 그 언어 이름 */
fun CityEntity.localNameOrNull(locale: Locale): String? = when (locale.language) {
    "ko" -> ko
    "ja" -> ja
    "zh" -> if (locale.script == "Hant" || locale.country in TRADITIONAL) zhHant else zhHans
    else -> en
}

/** 나라 이름(사용자 언어). 도시 데이터에는 없어서 Android Locale 로 */
fun countryName(countryCode: String, locale: Locale): String =
    Locale.Builder().setRegion(countryCode).build().getDisplayCountry(locale)

private val TRADITIONAL = setOf("TW", "HK", "MO")

fun CityEntity.toCity(locale: Locale, distanceKm: Double) = City(
    id = id,
    name = localName(locale),
    countryCode = cc,
    countryName = countryName(cc, locale),
    distanceKm = distanceKm,
    population = pop,
)
