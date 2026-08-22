package app.alpha.data

import app.alpha.analysis.EnergyCalibration
import app.alpha.analysis.SpectraFixtures
import app.alpha.analysis.SpectrumTemplate
import app.alpha.data.db.SpectrumTemplateDao
import app.alpha.data.db.SpectrumTemplateEntity
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * Библиотека шаблонов проверяется на настоящем спектре источника Th-232: весь
 * смысл правила «персонально для прибора» в том, какое разрешение приложение
 * припишет чужой форме, а выдуманный гауссиан этого не покажет.
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

        override suspend fun clear() = rows.clear()
    }

    private val thorium = SpectraFixtures.load("th232-source.csv")

    private suspend fun recorded(
        dao: FakeDao,
        serial: String?,
        fallback: Float = 0.084f,
    ): SpectrumTemplateEntity {
        val (counts, calibration) = thorium
        dao.rows.clear()
        TemplateRepository(dao).record(
            name = "Th-232",
            counts = counts,
            calibration = calibration,
            seconds = 27_714L,
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
        assertTrue(
            entity.resolution662 < 0.12f,
            "разрешение осталось паспортным: ${entity.resolution662}",
        )
        assertTrue(
            entity.resolution662 > 0.04f,
            "измеренное разрешение неправдоподобно мало: ${entity.resolution662}",
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
            repository.fitness(entity, serial = "rc-110-000000", resolution662 = 0.084f),
        )
        assertEquals(
            TemplateRepository.Fitness.FOREIGN,
            repository.fitness(entity, serial = "RC-103-000001", resolution662 = 0.084f),
        )
    }

    @Test
    fun `прибору с лучшим разрешением чужой шаблон не выдаётся`() = runTest {
        val dao = FakeDao()
        val repository = TemplateRepository(dao)
        val entity = recorded(dao, serial = null)
        // Сузить измеренную линию нечем: сведения о её форме утеряны прибором,
        // который снимал шаблон.
        assertEquals(
            TemplateRepository.Fitness.REFUSED,
            repository.fitness(entity, serial = null, resolution662 = 0.05f),
        )
        assertEquals(
            TemplateRepository.Fitness.FOREIGN,
            repository.fitness(entity, serial = null, resolution662 = 0.12f),
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
            seconds = 27_714L,
            gain = 1.0,
            offsetKeV = 0.0,
        )

        assertEquals(2 * 27_714L, seconds)
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
        // нём стоят ниже, чем в шаблоне. Смещение взято заметно больше
        // измеренных на приборе двух процентов, чтобы разница ширин не тонула
        // в погрешности самой подгонки линии.
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

        val before = strongestWidth(counts, calibration)

        val aligned = recorded(dao, serial = "RC-110-000000")
        repository.accumulate(aligned, second, calibration, 27_714L, gain = drift, offsetKeV = 0.0)
        val alignedWidth = strongestWidth(merged(dao), calibration)

        val blind = recorded(dao, serial = "RC-110-000000")
        repository.accumulate(blind, second, calibration, 27_714L, gain = 1.0, offsetKeV = 0.0)
        val blindWidth = strongestWidth(merged(dao), calibration)

        // Выравнивание кладёт линию на линию: ширина остаётся прежней.
        assertTrue(
            alignedWidth <= 1.05f * before,
            "выравнивание уширило линию: было $before кэВ, стало $alignedWidth кэВ",
        )
        // Слепое сложение кладёт рядом с линией её же сдвинутую копию: пара
        // сливается в структуру заметно шире исходной — то самое разрешение,
        // ради которого шаблон и снимают, при этом теряется.
        assertTrue(
            blindWidth > 1.05f * alignedWidth,
            "выровненное $alignedWidth кэВ, слепое $blindWidth кэВ",
        )
    }

    /** Счёт сохранённого шаблона. */
    private fun merged(dao: FakeDao): List<Int> = SpectrumBlob.decode(dao.rows.single().counts)

    /**
     * Ширина самой значимой линии спектра, кэВ. Именно её и портит сложение
     * без выравнивания: слабые линии тонут в шуме раньше, чем это станет видно.
     */
    private fun strongestWidth(counts: List<Int>, calibration: EnergyCalibration): Float =
        TemplateRepository.lines(counts, calibration, 0.084f)
            .maxBy { it.significance }
            .fwhmKeV!!

    @Test
    fun `счёт шаблона возвращается из базы без потерь`() = runTest {
        val dao = FakeDao()
        val entity = recorded(dao, serial = "RC-110-000000")
        val template = TemplateRepository(dao).template(entity)
        assertEquals(thorium.first, template.counts)
        assertEquals(27_714L, template.seconds)
    }
}
