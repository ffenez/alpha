package app.alpha.data

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.PeakDetection
import app.alpha.analysis.SpectrumTemplate
import app.alpha.analysis.SyntheticSpectra
import app.alpha.data.db.SpectrumTemplateDao
import app.alpha.data.db.SpectrumTemplateEntity
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * Библиотека шаблонов проверяется на ПОСТРОЕННОМ спектре источника Th-232
 * ([SyntheticSpectra.thoriumSource]): линии стоят на известных энергиях, их
 * ширина задана формой FWHM = R·√(662·E) при R = [RESOLUTION], и вопрос «какое
 * разрешение приложение припишет чужой форме» имеет проверяемый ответ.
 */
class TemplateRepositoryTest {

    private class FakeDao : SpectrumTemplateDao {
        val rows = mutableListOf<SpectrumTemplateEntity>()
        override suspend fun insert(template: SpectrumTemplateEntity): Long {
            rows += template.copy(id = rows.size + 1L)
            return rows.size.toLong()
        }

        override suspend fun update(template: SpectrumTemplateEntity) {
            val index = rows.indexOfFirst { it.id == template.id }
            if (index >= 0) rows[index] = template
        }

        override fun observeAll(): Flow<List<SpectrumTemplateEntity>> = flowOf(rows.toList())
        override suspend fun all(): List<SpectrumTemplateEntity> = rows.toList()
        override suspend fun count(): Long = rows.size.toLong()
        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun deleteForDevice(deviceSerial: String) {
            rows.removeAll { it.deviceSerial == deviceSerial }
        }

        override suspend fun clear() = rows.clear()
    }

    /** Ториевый источник: 3,14 млн импульсов за 7,7 ч на шкале серии. */
    private val thorium =
        SyntheticSpectra.thoriumSource(scale = 1.0) to SyntheticSpectra.CALIBRATION

    private suspend fun recorded(
        dao: FakeDao,
        serial: String?,
        fallback: Float = RESOLUTION,
    ): SpectrumTemplateEntity {
        val (counts, calibration) = thorium
        dao.rows.clear()
        TemplateRepository(dao).record(
            name = "Th-232",
            counts = counts,
            calibration = calibration,
            seconds = SECONDS,
            resolution662 = fallback,
            deviceSerial = serial,
            deviceName = if (serial != null) "RadiaCode-110" else null,
            atMillis = 1_700_000_000_000L,
        )
        return dao.rows.single()
    }

    @Test
    fun `разрешение шаблона измеряется по его спектру, а не берётся из паспорта`() = runTest {
        val dao = FakeDao()
        // Заведомо неверное паспортное значение: если бы его просто скопировали,
        // уширение чужой формы шло бы не туда.
        val entity = recorded(dao, serial = null, fallback = 0.15f)
        // Спектр построен при R = 0,084, измеряется 0,073 — на 13 % уже. Ширина
        // берётся по полувысоте над ЛИНЕЙНЫМ континуумом, а континуум оценён по
        // боковым полосам, и нижняя из них стоит на несимметричном хвосте линии:
        // завышенная подложка слева обрывает спуск раньше. Допуск ±30 % от
        // истины покрывает это смещение и оставляет паспортные 0,15 далеко за
        // границей.
        assertTrue(
            abs(entity.resolution662 - RESOLUTION) <= 0.3f * RESOLUTION,
            "измерено ${entity.resolution662} при истинных $RESOLUTION",
        )
    }

    @Test
    fun `шаблон из файла помечается импортированным, снятый — измеренным`() = runTest {
        val dao = FakeDao()
        assertEquals(
            SpectrumTemplateEntity.SOURCE_IMPORTED,
            recorded(dao, serial = null).source,
        )
        assertEquals(
            SpectrumTemplateEntity.SOURCE_MEASURED,
            recorded(dao, serial = "RC-110-000000").source,
        )
    }

    @Test
    fun `свой прибор узнаётся по серийнику независимо от регистра`() = runTest {
        val dao = FakeDao()
        val repository = TemplateRepository(dao)
        val entity = recorded(dao, serial = "RC-110-000000")
        assertEquals(
            TemplateRepository.Fitness.OWN,
            repository.fitness(entity, serial = "rc-110-000000", resolution662 = RESOLUTION),
        )
        assertEquals(
            TemplateRepository.Fitness.FOREIGN,
            repository.fitness(entity, serial = "RC-103-000001", resolution662 = RESOLUTION),
        )
    }

    @Test
    fun `прибору с лучшим разрешением чужой шаблон не выдаётся`() = runTest {
        val dao = FakeDao()
        val repository = TemplateRepository(dao)
        val entity = recorded(dao, serial = null)
        // Отсчёт от разрешения, ИЗМЕРЕННОГО по спектру шаблона: решение принимает
        // оно, а не паспорт. Шаг 0,01 с обеих сторон — впятеро больше допуска
        // [SpectrumTemplate.RESOLUTION_TOLERANCE] = 0,002, на который два прибора
        // одной модели считаются равными.
        // Сузить измеренную линию нечем: сведения о её форме утеряны прибором,
        // который снимал шаблон.
        assertEquals(
            TemplateRepository.Fitness.REFUSED,
            repository.fitness(entity, serial = null, resolution662 = entity.resolution662 - 0.01f),
        )
        assertEquals(
            TemplateRepository.Fitness.FOREIGN,
            repository.fitness(entity, serial = null, resolution662 = entity.resolution662 + 0.01f),
        )
    }

    @Test
    fun `досъёмка складывает счёт и время`() = runTest {
        val dao = FakeDao()
        val repository = TemplateRepository(dao)
        val entity = recorded(dao, serial = "RC-110-000000")
        val before = SpectrumBlob.decode(entity.counts)

        val seconds = repository.accumulate(
            entity = entity,
            counts = before,
            calibration = thorium.second,
            seconds = SECONDS,
            gain = 1.0,
            offsetKeV = 0.0,
        )

        assertEquals(2 * SECONDS, seconds)
        val after = SpectrumBlob.decode(dao.rows.single().counts)
        // Та же шкала — перекладки нет, счёт складывается канал в канал.
        assertEquals(before.map { it * 2 }, after)
    }

    @Test
    fun `досъёмка по измеренному сдвигу шкалы не портит разрешение`() = runTest {
        val dao = FakeDao()
        val repository = TemplateRepository(dao)
        val entity = recorded(dao, serial = "RC-110-000000")
        val (counts, calibration) = thorium

        // Второй сеанс той же формы, но шкала прибора уехала на −4 %: линии в
        // нём стоят ниже, чем в шаблоне. На 583,2 кэВ это 23 кэВ при ширине
        // линии 0,084·√(662·583,2) = 52 кэВ, то есть слепое сложение кладёт
        // рядом с линией её же копию на расстоянии почти половины ширины.
        val drift = 0.96
        val drifted = EnergyCalibration(
            a0 = (calibration.a0 * drift).toFloat(),
            a1 = (calibration.a1 * drift).toFloat(),
            a2 = (calibration.a2 * drift).toFloat(),
        )
        val second = assertNotNull(
            SpectrumTemplate.adapt(
                template = repository.template(entity),
                targetCalibration = drifted,
                targetChannels = counts.size,
                targetResolution662 = entity.resolution662,
            ),
        ).map { it.roundToInt() }

        // Эталон — два сеанса БЕЗ сдвига: шкала та же, счёт складывается канал
        // в канал, и ширина обязана остаться одиночной. Сравнивать с одиночным
        // сеансом нельзя: у него вдвое меньше статистика, а полувысота меряется
        // по отсчётам.
        val reference = recorded(dao, serial = "RC-110-000000")
        repository.accumulate(reference, counts, calibration, SECONDS, gain = 1.0, offsetKeV = 0.0)
        val referenceWidth = assertNotNull(lineNear(merged(dao), calibration, 583.2f)).fwhmKeV!!

        val aligned = recorded(dao, serial = "RC-110-000000")
        repository.accumulate(aligned, second, calibration, SECONDS, gain = drift, offsetKeV = 0.0)
        val alignedSpectrum = merged(dao)
        val alignedWidth = assertNotNull(lineNear(alignedSpectrum, calibration, 583.2f)).fwhmKeV!!

        val blind = recorded(dao, serial = "RC-110-000000")
        repository.accumulate(blind, second, calibration, SECONDS, gain = 1.0, offsetKeV = 0.0)
        val blindSpectrum = merged(dao)
        val blindWidth = assertNotNull(lineNear(blindSpectrum, calibration, 583.2f)).fwhmKeV!!

        // Выравнивание кладёт линию на линию: ширина остаётся прежней.
        assertTrue(
            alignedWidth <= 1.05f * referenceWidth,
            "выравнивание уширило линию: было $referenceWidth кэВ, стало $alignedWidth кэВ",
        )
        // Слепое сложение кладёт рядом с линией её же сдвинутую копию: пара
        // сливается в структуру заметно шире исходной — то самое разрешение,
        // ради которого шаблон и снимают, при этом теряется.
        assertTrue(
            blindWidth > 1.05f * alignedWidth,
            "выровненное $alignedWidth кэВ, слепое $blindWidth кэВ",
        )
        // На 2614,5 кэВ сдвиг −4 % равен целой ширине линии (109 кэВ против
        // 110): слепое сложение разносит её в дублет, который гейт формы не
        // признаёт линией вовсе — линия пропадает из спектра, а не расширяется.
        assertNotNull(lineNear(alignedSpectrum, calibration, 2614.5f))
        assertNull(lineNear(blindSpectrum, calibration, 2614.5f))
    }

    /** Счёт сохранённого шаблона. */
    private fun merged(dao: FakeDao): List<Int> = SpectrumBlob.decode(dao.rows.single().counts)

    /**
     * Линия с измеренной шириной, ближайшая к [energyKeV]; null — такой линии в
     * спектре нет. Допуск — половина ожидаемой ширины: центр тяжести линии с
     * хвостом смещён вниз на единицы кэВ, а вдвое дальше стоит уже другая линия.
     */
    private fun lineNear(counts: List<Int>, calibration: EnergyCalibration, energyKeV: Float) =
        TemplateRepository.lines(counts, calibration, RESOLUTION)
            .filter {
                abs(it.energyKeV - energyKeV) < 0.5f * PeakDetection.fwhmKeV(energyKeV, RESOLUTION)
            }
            .minByOrNull { abs(it.energyKeV - energyKeV) }

    @Test
    fun `собранный фон заводится сам и заменяется только заметно длиннее`() = runTest {
        val dao = FakeDao()
        val repository = TemplateRepository(dao)
        val counts = SyntheticSpectra.naturalBackground(scale = 1.0)
        val calibration = SyntheticSpectra.CALIBRATION

        assertTrue(
            repository.refreshAutoBackground(
                counts = counts,
                calibration = calibration,
                seconds = 10_000L,
                resolution662 = RESOLUTION,
                deviceSerial = "RC-110-000000",
                deviceName = null,
                atMillis = 1_700_000_000_000L,
            ),
            "первый собранный фон обязан появиться",
        )
        assertEquals(SpectrumTemplateEntity.SOURCE_AUTO, dao.rows.single().source)

        // Прибавка в проценты не повод переписывать библиотеку: форма от неё
        // точнее не станет.
        assertTrue(
            !repository.refreshAutoBackground(
                counts = counts,
                calibration = calibration,
                seconds = 11_000L,
                resolution662 = RESOLUTION,
                deviceSerial = "RC-110-000000",
                deviceName = null,
                atMillis = 1_700_000_100_000L,
            ),
            "фон переписан ради 10 % накопления",
        )
        assertEquals(10_000L, dao.rows.single().durationSeconds)

        assertTrue(
            repository.refreshAutoBackground(
                counts = counts,
                calibration = calibration,
                seconds = 20_000L,
                resolution662 = RESOLUTION,
                deviceSerial = "RC-110-000000",
                deviceName = null,
                atMillis = 1_700_000_200_000L,
            ),
            "вдвое более длинное накопление обязано заменить прежнее",
        )
        assertEquals(1, dao.rows.size, "собранный фон обязан оставаться одной записью")
        assertEquals(20_000L, dao.rows.single().durationSeconds)
    }

    @Test
    fun `счёт шаблона возвращается из базы без потерь`() = runTest {
        val dao = FakeDao()
        val entity = recorded(dao, serial = "RC-110-000000")
        val template = TemplateRepository(dao).template(entity)
        assertEquals(thorium.first, template.counts)
        assertEquals(SECONDS, template.seconds)
    }

    private companion object {
        /** Разрешение прибора, при котором построен спектр шаблона. */
        const val RESOLUTION = 0.084f

        /** Время накопления шаблона, с: 7,7 ч. */
        const val SECONDS = 27_714L
    }
}
