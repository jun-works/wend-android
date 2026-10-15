package app.wend.sea.data.analysis

import app.wend.sea.data.geo.CityEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TripTitleTest {
    private val korea = Locale.KOREA
    private val kyoto = city("JP", en = "Kyoto", ko = "교토")
    private val tsoohor = city("MN", en = "Tsoohor", ko = null)
    private val gangneung = city("KR", en = "Gangneung", ko = "강릉")

    @Test
    fun mostlyOneCityWithLocalName() = assertEquals("교토", tripTitle(kyoto, .8f, "JP", korea))

    @Test
    fun spreadOverCountry() = assertEquals("일본", tripTitle(kyoto, .3f, "JP", korea))

    @Test
    fun mainCityWithoutLocalName() = assertEquals("몽골", tripTitle(tsoohor, .9f, "MN", korea))

    @Test
    fun mongoliaRoadTrip() = assertEquals("몽골", tripTitle(tsoohor, .24f, "MN", korea))

    @Test
    fun domesticTripKeepsCity() = assertEquals("강릉", tripTitle(gangneung, .3f, "KR", korea))

    @Test
    fun otherLanguageUsesEnglishCityName() = assertEquals("Kyoto", tripTitle(kyoto, .8f, "JP", Locale.US))

    @Test
    fun noCity() = assertNull(tripTitle(null, 0f, null, korea))

    private fun city(cc: String, en: String, ko: String?) =
        CityEntity(id = 1, lat = 0, lon = 0, cell = 0, cc = cc, pop = 0, en = en, ko = ko, ja = null, zhHans = null, zhHant = null)
}
