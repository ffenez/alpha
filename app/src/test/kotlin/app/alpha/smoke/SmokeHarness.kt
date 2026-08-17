package app.alpha.smoke

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.alpha.AppGraph
import app.alpha.data.AppSettings
import app.alpha.data.SpectrumBlob
import app.alpha.data.db.AppDatabase
import app.alpha.data.db.MeasurementSessionEntity
import app.alpha.data.db.SampleEntity
import app.alpha.data.db.SpectrumSnapshotEntity
import app.alpha.data.db.TrackPointEntity
import app.alpha.data.db.TrackSessionEntity
import app.alpha.ui.text.AppLanguage
import app.alpha.ui.text.LocalStrings
import app.alpha.ui.text.stringsFor
import app.alpha.ui.theme.AppSkin
import app.alpha.ui.theme.AppTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * Сочетание скин × тема × язык, в котором смоук ставит экран. Двух вариантов
 * достаточно: вместе они покрывают оба скина, обе темы и оба языка, не
 * умножая прогон на восемь.
 */
data class UiVariant(val skin: AppSkin, val dark: Boolean, val language: AppLanguage) {
    override fun toString() = "${skin.id}-${if (dark) "dark" else "light"}-${language.id}"

    companion object {
        val ALL = listOf(
            UiVariant(AppSkin.TERMINAL, dark = true, language = AppLanguage.RU),
            UiVariant(AppSkin.EIGHT_BIT, dark = false, language = AppLanguage.EN),
        )

        fun of(id: String): UiVariant = ALL.first { it.toString() == id }

        /** Параметры ParameterizedRobolectricTestRunner — строками, по имени. */
        @JvmStatic
        fun parameters(): List<Array<Any>> = ALL.map { arrayOf<Any>(it.toString()) }
    }
}

/**
 * Ставит экран в [AppTheme] выбранного варианта и даёт композиции осесть.
 * Явный [LocalStrings] — тот же провайдер, что в MainActivity: экраны берут
 * язык из каталога, а не из системы.
 */
fun ComposeContentTestRule.showScreen(variant: UiVariant, content: @Composable () -> Unit) {
    setContent {
        AppTheme(dark = variant.dark, skin = variant.skin) {
            CompositionLocalProvider(LocalStrings provides stringsFor(variant.language)) {
                content()
            }
        }
    }
    settle()
}

/**
 * Прокачивает композицию несколько кадров подряд, чередуя с реальным сном:
 * загрузки экранов уходят на Dispatchers.IO, и их результат (или их падение)
 * обязан успеть приземлиться в композицию до конца теста.
 */
fun ComposeContentTestRule.settle(passes: Int = 6) {
    repeat(passes) {
        waitForIdle()
        Thread.sleep(40)
    }
    waitForIdle()
}

/** Фабрика изолированных тестовых графов и наполнение базы для смоука. */
object Smoke {

    private val nextStore = AtomicInteger(0)

    /**
     * Граф на in-memory Room и СВОЁМ файле DataStore: делегат
     * `preferencesDataStore` — процессный синглтон, а Robolectric держит один
     * процесс на много тестов, поэтому каждый граф получает уникальный файл.
     */
    fun graph(): AppGraph {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = {
                File(context.cacheDir, "smoke-${nextStore.incrementAndGet()}.preferences_pb")
            },
        )
        return AppGraph.createForTest(context, db, AppSettings(store))
    }

    data class Seeded(
        val sessionId: Long,
        val spectrumId: Long,
        val secondSpectrumId: Long,
        val trackSessionId: Long,
    )

    /** Правдоподобный фоновый спектр: экспоненциальный континуум + пик ~662 кэВ. */
    private fun spectrum(atMillis: Long, seconds: Long): SpectrumSnapshotEntity {
        val counts = List(1024) { ch ->
            val peak = exp(-((ch - 221.0) * (ch - 221.0)) / (2.0 * 15.0 * 15.0))
            (200.0 * exp(-ch / 300.0) + 80.0 * peak).toInt() + 1
        }
        return SpectrumSnapshotEntity(
            timestamp = atMillis,
            accumulated = true,
            origin = SpectrumSnapshotEntity.ORIGIN_USER,
            durationSeconds = seconds,
            a0 = 0f,
            a1 = 3f,
            a2 = 0f,
            channelCount = 1024,
            counts = SpectrumBlob.encode(counts),
        )
    }

    /**
     * «База с горсткой измерений»: 150 секундных отсчётов, закрытая сессия,
     * два снимка спектра (для снимка и компаратора) и короткий трек.
     */
    fun seed(graph: AppGraph, nowMillis: Long = System.currentTimeMillis()): Seeded =
        runBlocking {
            val db = graph.database
            val start = nowMillis - 150_000L
            db.sampleDao().insertAll(
                (0 until 150).map { i ->
                    SampleEntity(
                        timestamp = start + i * 1000L,
                        doseRate = 0.11f + 0.02f * (i % 5),
                        doseRateErr = 0.012f,
                        countRate = 9.0f + (i % 7),
                        countRateErr = 0.9f,
                        flags = 0,
                        realTimeFlags = 0,
                    )
                },
            )
            val sessionId = db.sessionDao().insert(
                MeasurementSessionEntity(profileId = null, startedAt = start, endedAt = nowMillis),
            )
            val spectrumId = db.spectrumDao().insert(spectrum(nowMillis - 60_000L, seconds = 3600))
            val secondId = db.spectrumDao().insert(spectrum(nowMillis - 30_000L, seconds = 1800))
            val trackId = db.trackDao().insertSession(
                TrackSessionEntity(name = "smoke", startedAt = start, endedAt = nowMillis),
            )
            for (i in 0 until 30) {
                db.trackDao().insertPoint(
                    TrackPointEntity(
                        sessionId = trackId,
                        timestamp = start + i * 5000L,
                        latitude = 55.75 + i * 1e-4,
                        longitude = 37.61 + i * 1e-4,
                        accuracyMeters = 8f,
                        doseRate = 0.12f,
                        countRate = 10f,
                        altitudeMeters = 150.0,
                    ),
                )
            }
            Seeded(sessionId, spectrumId, secondId, trackId)
        }
}
