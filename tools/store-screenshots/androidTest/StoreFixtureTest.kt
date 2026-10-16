package app.wend.sea

import android.content.ContentValues
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.exifinterface.media.ExifInterface
import app.wend.sea.data.AnalysisSummary
import app.wend.sea.data.UserRepository
import app.wend.sea.data.analysis.FeatureReader
import app.wend.sea.data.local.*
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in screenshot-only fixture. Never runs in the production package or a physical device. */
@RunWith(AndroidJUnit4::class)
class StoreFixtureTest {
    @Test fun seed() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "app.wend.sea.screenshots")
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val locale = InstrumentationRegistry.getArguments().getString("locale") ?: "en-US"
        val copy = org.json.JSONObject(instrumentation.context.assets.open("$locale.json").bufferedReader().use { it.readText() }).getJSONObject("fixture")
        val labels = copy.getJSONObject("places")
        val db = DatabaseModule.database(context)
        val dao = db.tripDao()
        val marker = File(context.filesDir, "store-fixture-v1")
        check(marker.exists() || dao.trips().isEmpty()) { "Refusing to replace non-fixture data" }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        // Only remove our generated demo photos, in the isolated emulator application.
        resolver.delete(collection, "${MediaStore.Images.Media.RELATIVE_PATH} = ?", arrayOf("Pictures/WendStore/"))
        dao.resetUserChoices()
        val trips = mutableListOf<TripEntity>()
        val media = mutableListOf<TripMediaEntity>()
        val names = mutableListOf<UserNameEntity>()
        val locations = mutableListOf<MediaLocationEntity>()
        val features = mutableListOf<MediaFeatureEntity>()
        val best = mutableListOf<BestShotEntity>()
        val reader = FeatureReader(context)
        data class Scene(val asset: String, val lat: Double, val lon: Double, val name: String)
        val italy = listOf(
            Scene("braies", 46.694, 12.085, labels.getString("dolomites")),
            Scene("tre-cime", 46.636, 12.301, labels.getString("tre-cime")),
            Scene("braies", 46.694, 12.085, labels.getString("braies")),
        )
        for (number in 1..3) {
            val start = Instant.parse(listOf("2024-06-12T00:00:00Z", "2025-09-15T00:00:00Z", "2026-07-18T00:00:00Z")[number-1]).toEpochMilli()
            val scenes = if (number == 2) listOf(Scene("iceland",63.532, -19.511, labels.getString("iceland"))) else italy
            val tripName = copy.getJSONArray("trips").getString(number - 1)
            var count = 0
            scenes.forEachIndexed { sceneIndex, scene ->
                // Two bursts per stop, three identical demo exposures per burst.
                repeat(2) { burst -> repeat(3) { shot ->
                    val taken = start + listOf(0L, 14_400_000L, 86_400_000L)[sceneIndex] + burst * 600_000L + shot * 2_000L
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "wend-store-$number-$sceneIndex-$burst-$shot.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WendStore/")
                        put(MediaStore.Images.Media.DATE_TAKEN, taken)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = checkNotNull(resolver.insert(collection, values))
                    val source = File(context.cacheDir, "store-fixture-photo.jpg")
                    instrumentation.context.assets.open((if (sceneIndex == 0 && number != 2 && burst == 1) "dolomites" else scene.asset) + ".jpg").use { input -> source.outputStream().use { input.copyTo(it) } }
                    ExifInterface(source).apply {
                        val stamp = java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss").withZone(java.time.ZoneOffset.UTC).format(Instant.ofEpochMilli(taken))
                        setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
                        setAttribute(ExifInterface.TAG_DATETIME, stamp)
                        setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "+00:00")
                        setLatLong(scene.lat, scene.lon)
                        saveAttributes()
                    }
                    resolver.openOutputStream(uri)!!.use { output -> source.inputStream().use { it.copyTo(output) } }
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0); put(MediaStore.Images.Media.DATE_TAKEN, taken) }, null, null)
                    val id = ContentUris.parseId(uri)
                    val modified = resolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_MODIFIED), null, null, null)!!.use { it.moveToFirst(); it.getLong(0) }
                    resolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)!!.use { check(it.moveToFirst() && !it.isNull(0) && it.getLong(0) > 0) { "Demo photo missing capture date" } }
                    media += TripMediaEntity(number, id, false)
                    names += UserNameEntity(id, false, UserNameEntity.KIND_TRIP, tripName)
                    names += UserNameEntity(id, false, UserNameEntity.KIND_PLACE, scene.name)
                    locations += MediaLocationEntity(id, false, modified, scene.lat, scene.lon)
                    features += MediaFeatureEntity.of(id, modified, checkNotNull(reader.read(id)))
                    if (shot == 0) best += BestShotEntity(id)
                    count++
                } }
            }
            trips += TripEntity(number, start, start + (if (scenes.size > 1) 86_400_000L else 0L) + 604_000L, count, cityId=0, cityShare=0f, countryCode=if(number==2) "IS" else "IT")
        }
        dao.replaceAll(trips, media)
        dao.upsertNames(names)
        dao.insertBestShots(best)
        db.mediaLocationDao().upsert(locations)
        db.mediaFeatureDao().upsert(features)
        UserRepository(context).apply {
            saveNickname("Wend")
            setOnboardingDone()
            saveAnalysisSummary(AnalysisSummary(media.size, media.size, trips.size, false))
            markAnalysisResultSeen()
        }
        marker.writeText("Synthetic dates/locations and duplicate bursts; licensed stock photos.\n")
        db.close()
    }
}
