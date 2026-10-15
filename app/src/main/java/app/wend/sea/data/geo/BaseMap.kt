package app.wend.sea.data.geo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/** 기본 지도 레이어(Natural Earth). 그리는 순서와 같다. */
enum class MapLayer(val polygon: Boolean) {
    Land(true),
    Lake(true),
    River(false),
    Border(false),
    Province(false),
    Road(false),
}

/** 경도·위도 쌍이 번갈아 든 좌표 묶음(다각형이면 바깥 고리 + 구멍들) */
class MapShape(val rings: List<DoubleArray>)

class MapCity(val latitude: Double, val longitude: Double, val rank: Int, val name: String)

class MapArea(val shapes: Map<MapLayer, List<MapShape>>, val cities: List<MapCity>)

/**
 * 앱에 넣어 둔 오프라인 종이 지도(assets/basemap.bin, tools/geo/build_basemap.py 로 만듦).
 * 인터넷을 쓰지 않는다. 2° 칸 단위로 필요한 곳만 풀어서 쓴다.
 */
@Singleton
class BaseMap @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock = Mutex()
    private var data: ByteBuffer? = null
    private var cellDeg = 2
    private val cells = HashMap<Long, Pair<Int, Int>>()
    private var cities: List<MapCity> = emptyList()
    private val decoded = object : LinkedHashMap<Long, Map<MapLayer, List<MapShape>>>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Map<MapLayer, List<MapShape>>>?) = size > 48
    }

    /** 위도·경도 범위 안의 지도 조각과 도시 */
    suspend fun area(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): MapArea = withContext(Dispatchers.IO) {
        lock.withLock {
            val buffer = data ?: load()
            val result = MapLayer.entries.associateWith { mutableListOf<MapShape>() }
            for (li in floor(minLat / cellDeg).toInt()..floor(maxLat / cellDeg).toInt()) {
                for (lo in floor(minLon / cellDeg).toInt()..floor(maxLon / cellDeg).toInt()) {
                    val key = key(li, lo)
                    val cell = decoded[key] ?: cells[key]?.let { (offset, length) -> decodeCell(buffer, li, lo, offset, length) }?.also { decoded[key] = it }
                    cell?.forEach { (layer, shapes) -> result.getValue(layer) += shapes }
                }
            }
            MapArea(result, cities.filter { it.latitude in minLat..maxLat && it.longitude in minLon..maxLon })
        }
    }

    private fun load(): ByteBuffer {
        val bytes = context.assets.open("basemap.bin").use { it.readBytes() }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(bytes.copyOfRange(0, 4).decodeToString() == "WBM1") { "basemap.bin 형식이 다름" }
        b.position(4)
        cellDeg = b.get().toInt()
        repeat(b.int) {
            val li = b.short.toInt()
            val lo = b.short.toInt()
            cells[key(li, lo)] = b.int to b.int
        }
        val placeCount = b.int
        cities = List(placeCount) {
            val lat = b.int / SCALE
            val lon = b.int / SCALE
            val rank = b.get().toInt() and 0xFF
            val name = ByteArray(b.get().toInt() and 0xFF).also { b.get(it) }.decodeToString()
            MapCity(lat, lon, rank, name)
        }
        data = b
        return b
    }

    private fun decodeCell(buffer: ByteBuffer, li: Int, lo: Int, offset: Int, length: Int): Map<MapLayer, List<MapShape>> {
        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        b.position(offset)
        b.limit(offset + length)
        val originLat = Math.round(li * cellDeg * SCALE)
        val originLon = Math.round(lo * cellDeg * SCALE)
        return MapLayer.entries.associateWith {
            List(varint(b)) {
                MapShape(
                    List(varint(b)) {
                        val n = varint(b)
                        val ring = DoubleArray(n * 2)
                        var lat = originLat
                        var lon = originLon
                        for (i in 0 until n) {
                            lat += unzigzag(varint(b))
                            lon += unzigzag(varint(b))
                            ring[i * 2] = lon / SCALE
                            ring[i * 2 + 1] = lat / SCALE
                        }
                        ring
                    },
                )
            }
        }
    }

    private fun varint(b: ByteBuffer): Int {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = b.get().toInt() and 0xFF
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result.toInt()
            shift += 7
        }
    }

    private fun unzigzag(n: Int): Long = ((n ushr 1).toLong()) xor -((n and 1).toLong())

    private fun key(li: Int, lo: Int): Long = (li.toLong() shl 32) or (lo.toLong() and 0xFFFFFFFFL)

    private companion object {
        const val SCALE = 1e4
    }
}
