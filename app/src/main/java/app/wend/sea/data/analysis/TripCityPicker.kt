package app.wend.sea.data.analysis

import app.wend.sea.data.geo.CityEntity
import app.wend.sea.data.geo.CityFinder
import app.wend.sea.data.geo.countryName
import app.wend.sea.data.geo.localName
import app.wend.sea.data.geo.localNameOrNull
import app.wend.sea.data.local.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/** 여행에서 사진이 가장 많은 도시와 그 비율(0–1), 사진이 가장 많은 나라 */
data class TripCity(val cityId: Long, val share: Float, val countryCode: String?)

/** 장소마다 가장 가까운 도시를 찾아 사진 수로 센다 */
class TripCityPicker @Inject constructor(private val cityFinder: CityFinder) {

    /** 장소에 위치가 없거나 근처에 도시가 없으면 cityId 가 [TripEntity.NO_CITY] */
    suspend fun pick(media: List<MediaPoint>): TripCity {
        val places = withContext(Dispatchers.Default) { PlaceGrouper().group(media) }
        val byCity = LinkedHashMap<Long, Int>()
        val byCountry = LinkedHashMap<String, Int>()
        for (place in places) {
            val lat = place.latitude ?: continue
            val lon = place.longitude ?: continue
            val city = cityFinder.nearestCity(lat, lon, Locale.ROOT) ?: continue
            byCity.merge(city.id, place.media.size, Int::plus)
            byCountry.merge(city.countryCode, place.media.size, Int::plus)
        }
        val top = byCity.maxByOrNull { it.value } ?: return TripCity(TripEntity.NO_CITY, 0f, null)
        return TripCity(top.key, top.value.toFloat() / byCity.values.sum(), byCountry.maxBy { it.value }.key)
    }
}

/** 한 도시에서 이만큼 넘게 찍어야 도시 이름을 여행 이름으로 쓴다 */
const val MAIN_CITY_SHARE = .5f

/**
 * 여행 이름. 한 도시에서 [MAIN_CITY_SHARE] 이상 찍었고 그 도시에 사용자 언어 이름이 있으면 도시("교토"),
 * 아니면 나라("몽골"). 나라가 폰 지역과 같으면(국내 여행) 나라 이름은 쓸모가 없어 도시. 도시가 없으면 null.
 */
fun tripTitle(city: CityEntity?, share: Float, countryCode: String?, locale: Locale): String? {
    if (city == null) return null
    if (share >= MAIN_CITY_SHARE && city.localNameOrNull(locale) != null) return city.localName(locale)
    if (countryCode == null || countryCode == locale.country) return city.localName(locale)
    return countryName(countryCode, locale)
}
