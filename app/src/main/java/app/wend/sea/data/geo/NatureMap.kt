package app.wend.sea.data.geo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 자연 지명의 종류. [tint] 면 지도에 영역을 옅게 칠한다 */
enum class NatureKind(val tint: Boolean) {
    Desert(true), Range(true), Plateau(true), Plain(false), Basin(false), Valley(false), Land(false), Island(false), Sea(false), Peak(false),
}

/**
 * 사막·산맥·고원·섬·바다·산봉우리 이름(사용자 언어). [rings] 는 칠할 영역(경도·위도 쌍), [rank] 가 작을수록 큰 지명,
 * [elevation] 은 산봉우리 높이(m).
 */
class MapNature(
    val kind: NatureKind,
    val rank: Float,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val rings: List<DoubleArray>,
    val elevation: Int,
)

/**
 * 앱에 넣어 둔 자연 지명(assets/geo/nature.json, tools/geo/build_nature.py 로 만듦, Natural Earth).
 * 인터넷을 쓰지 않는다.
 */
@Singleton
class NatureMap @Inject constructor(@ApplicationContext private val context: Context) {
    private class Raw(
        val kind: NatureKind,
        val rank: Float,
        val names: Map<String, String>,
        val lat: Double,
        val lon: Double,
        val rings: List<DoubleArray>,
        val elevation: Int,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
    )

    private val lock = Mutex()
    private var all: List<Raw>? = null

    /** 범위 안(영역이 걸치거나 이름 자리가 들어오는) 자연 지명 */
    suspend fun area(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, locale: Locale): List<MapNature> = withContext(Dispatchers.IO) {
        // 읽지 못해도 지도는 그린다(자연 지명만 빠짐)
        val items = lock.withLock { all ?: runCatching { load() }.getOrDefault(emptyList()).also { all = it } }
        items.filter { it.maxLat >= minLat && it.minLat <= maxLat && it.maxLon >= minLon && it.minLon <= maxLon }
            .map { MapNature(it.kind, it.rank, localName(it.names, locale), it.lat, it.lon, it.rings, it.elevation) }
    }

    private fun load(): List<Raw> {
        val text = context.assets.open("geo/nature.json").bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val n = o.getJSONObject("n")
            val names = n.keys().asSequence().associateWith { n.getString(it) }
            val p = o.getJSONArray("p")
            val lat = p.getDouble(0)
            val lon = p.getDouble(1)
            // 파일은 위도·경도 쌍, 지도는 경도·위도 쌍
            val rings = o.optJSONArray("g")?.let { g ->
                List(g.length()) { r ->
                    val flat = g.getJSONArray(r)
                    DoubleArray(flat.length()) { k -> if (k % 2 == 0) flat.getDouble(k + 1) else flat.getDouble(k - 1) }
                }
            }.orEmpty()
            var minLat = lat; var maxLat = lat; var minLon = lon; var maxLon = lon
            rings.forEach { ring ->
                for (k in 0 until ring.size / 2) {
                    minLon = minOf(minLon, ring[k * 2]); maxLon = maxOf(maxLon, ring[k * 2])
                    minLat = minOf(minLat, ring[k * 2 + 1]); maxLat = maxOf(maxLat, ring[k * 2 + 1])
                }
            }
            Raw(kindOf(o.getString("k")), o.getDouble("r").toFloat(), names, lat, lon, rings, o.optInt("e"), minLat, maxLat, minLon, maxLon)
        }
    }

    private fun kindOf(k: String) = when (k) {
        "desert" -> NatureKind.Desert
        "range" -> NatureKind.Range
        "plateau" -> NatureKind.Plateau
        "plain" -> NatureKind.Plain
        "basin" -> NatureKind.Basin
        "valley" -> NatureKind.Valley
        "island" -> NatureKind.Island
        "sea" -> NatureKind.Sea
        "peak" -> NatureKind.Peak
        else -> NatureKind.Land
    }

    /** 도시 이름과 같은 규칙: ko · ja · zh(번체: Hant 또는 TW·HK·MO) 외에는 영어, 없으면 영어 */
    private fun localName(names: Map<String, String>, locale: Locale): String {
        val key = when (locale.language) {
            "ko" -> "ko"
            "ja" -> "ja"
            "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zht" else "zh"
            else -> "en"
        }
        return names[key] ?: names["en"].orEmpty()
    }
}
