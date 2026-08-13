package app.radiacode.service

import app.radiacode.analysis.Spectrogram
import app.radiacode.analysis.SpectrogramHistory
import app.radiacode.data.SpectrogramRepository
import app.radiacode.data.db.SpectrogramDao
import app.radiacode.data.db.SpectrogramSliceEntity
import app.radiacode.protocol.Spectrum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Сквозная запись спектрограммы (ADR 007): срез уходит в базу, а после
 * перезапуска процесса картинка поднимается из неё, а не начинается с пустоты.
 */
class SpectrogramStoreTest {

    /** Хранилище в памяти с ровно той же семантикой, что у SQL DAO. */
    private class FakeDao : SpectrogramDao() {
        val rows = LinkedHashMap<Long, SpectrogramSliceEntity>()

        override suspend fun upsert(slices: List<SpectrogramSliceEntity>) {
            for (slice in slices) rows[slice.startMillis] = slice
        }

        override suspend fun window(from: Long, to: Long, limit: Int) =
            rows.values.filter { it.endMillis >= from && it.startMillis <= to }
                .sortedByDescending { it.startMillis }
                .take(limit)

        override suspend fun range(from: Long, to: Long, limit: Int) =
            rows.values.filter { it.startMillis >= from && it.startMillis < to }
                .sortedBy { it.startMillis }
                .take(limit)

        override suspend fun deleteRange(from: Long, to: Long) {
            rows.keys.filter { it >= from && it < to }.forEach { rows.remove(it) }
        }

        override suspend fun count() = rows.size

        override suspend fun earliestStart() = rows.keys.minOrNull()

        override suspend fun nextShortSliceStart(from: Long, to: Long, maxDurationMillis: Long) =
            rows.values
                .filter {
                    it.startMillis >= from && it.startMillis < to &&
                        it.durationMillis < maxDurationMillis
                }
                .minOfOrNull { it.startMillis }
    }

    /** Накопленный с момента сброса спектр: один «горячий» канал растёт. */
    private fun spectrum(seconds: Long, counts: Int) = Spectrum(
        durationSeconds = seconds,
        a0 = 0f,
        a1 = 3f,
        a2 = 0f,
        counts = List(1024) { if (it == 100) counts else 0 },
    )

    @Test
    fun `the picture comes back from the database after a restart`() = runTest {
        val dao = FakeDao()
        val repository = SpectrogramRepository(dao)
        val store = SpectrogramStore(repository)
        // Первый опрос только взводит базу разности — среза из него не бывает.
        store.onSpectrum(spectrum(30, 0), atMillis = 30_000L, cps = 20f, doseMicroSvH = 0.14f)
        assertTrue(store.slices.value.isEmpty())
        store.onSpectrum(spectrum(60, 600), atMillis = 60_000L, cps = 21f, doseMicroSvH = 0.15f)
        store.onSpectrum(spectrum(90, 1_200), atMillis = 90_000L, cps = 22f, doseMicroSvH = 0.16f)
        assertEquals(2, store.slices.value.size)
        assertEquals(2, dao.rows.size)

        // Процесс перезапустился: новое кольцо пусто, база — нет.
        val afterRestart = SpectrogramStore(repository)
        assertTrue(afterRestart.slices.value.isEmpty())
        afterRestart.restore(nowMillis = 95_000L)

        val restored = afterRestart.slices.value
        assertEquals(2, restored.size)
        assertEquals(store.slices.value.map { it.timestampMillis }, restored.map { it.timestampMillis })
        assertEquals(store.slices.value.map { it.totalCounts }, restored.map { it.totalCounts })
        // Экспозиция — приборная, а не настенная: 30 с накопления на срез.
        assertTrue(restored.all { it.intervalSeconds == 30L })
        assertEquals(600f, restored.first().totalCounts)
    }

    @Test
    fun `restore never overwrites a picture that is already live`() = runTest {
        val dao = FakeDao()
        val store = SpectrogramStore(SpectrogramRepository(dao))
        store.onSpectrum(spectrum(30, 0), atMillis = 30_000L, cps = null, doseMicroSvH = null)
        store.onSpectrum(spectrum(60, 600), atMillis = 60_000L, cps = null, doseMicroSvH = null)
        val live = store.slices.value
        store.restore(nowMillis = 60_000L)
        assertEquals(live, store.slices.value)
    }

    @Test
    fun `old history is thinned, and the counts survive the thinning`() = runTest {
        val dao = FakeDao()
        val repository = SpectrogramRepository(dao)
        val store = SpectrogramStore(repository)
        // Начало выровнено по сетке пятиминутных корзин НАМЕРЕННО: корзины
        // прореживания привязаны к стенным часам (`startMillis / target`), а не
        // к первому срезу, иначе повторный проход двигал бы границы и результат
        // зависел бы от того, когда его запустили. При невыровненном начале те
        // же двадцать срезов честно лягут в три корзины — крайние неполные.
        val start = 1_000_000_000_000L / SpectrogramHistory.COMPACTED_SLICE_MILLIS *
            SpectrogramHistory.COMPACTED_SLICE_MILLIS
        // Двадцать тридцатисекундных срезов подряд — десять минут записи.
        store.onSpectrum(spectrum(0, 0), atMillis = start, cps = null, doseMicroSvH = null)
        for (i in 1..20) {
            store.onSpectrum(
                spectrum(30L * i, 300 * i),
                atMillis = start + 30_000L * i,
                cps = null,
                doseMicroSvH = null,
            )
        }
        assertEquals(20, dao.rows.size)
        val countsBefore = dao.rows.values.sumOf { it.durationMillis }

        // «Сейчас» — восемь суток спустя: всё записанное старше недели.
        val now = start + 8L * 24 * 3_600_000L
        val removed = repository.compact(now)
        assertEquals(18, removed, "20 срезов по 30 с ложатся в 2 пятиминутных")
        assertEquals(2, dao.rows.size)
        assertEquals(countsBefore, dao.rows.values.sumOf { it.durationMillis })
        assertTrue(dao.rows.values.all { it.durationMillis == SpectrogramHistory.COMPACTED_SLICE_MILLIS })
        // Импульсы не потерялись: сумма по полосам сохраняется.
        val total = dao.rows.values.sumOf { entity ->
            app.radiacode.data.SpectrumBlob.decode(entity.counts).sum()
        }
        assertEquals(300 * 20, total)
        // Повторный проход ничего больше не трогает.
        assertEquals(0, repository.compact(now))
        // И не тратит проходов на историю, в которой уже нечего сливать:
        // курсор прыгает по срезам короче цели, а таких не осталось.
        assertEquals(null, dao.nextShortSliceStart(0L, now, SpectrogramHistory.COMPACTED_SLICE_MILLIS))
    }

    @Test
    fun `the band scheme of a written slice is the current one`() = runTest {
        val dao = FakeDao()
        val store = SpectrogramStore(SpectrogramRepository(dao))
        store.onSpectrum(spectrum(0, 0), atMillis = 0L, cps = null, doseMicroSvH = null)
        store.onSpectrum(spectrum(5, 50), atMillis = 5_000L, cps = null, doseMicroSvH = null)
        val row = dao.rows.values.single()
        assertEquals(app.radiacode.analysis.SpectrogramBinning.CURRENT_SCHEME, row.schemeId)
        assertEquals(Spectrogram.BAND_COUNT, row.bandCount)
        assertEquals(0L, row.startMillis)
        assertEquals(5_000L, row.endMillis)
        assertEquals(5_000L, row.durationMillis)
    }
}
