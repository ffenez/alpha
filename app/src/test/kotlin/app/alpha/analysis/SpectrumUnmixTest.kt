package app.alpha.analysis

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Разложение по шаблонам проверяется на РЕАЛЬНЫХ формах: измеренный источник
 * Th-232 (7,7 ч, 3,1 млн импульсов) и собственный фон прибора (71 ч, 6,3 млн).
 *
 * Синтетика тут бесполезна: весь вопрос в том, как ведут себя настоящий
 * комптоновский континуум и настоящие хвосты линий, а их не выдумать.
 *
 * Замкнутый цикл: из реальной формы строится «измерение» с известной долей и
 * пуассоновским шумом, а подгонка обязана вернуть эту долю и подобранное
 * усиление. Если она не находит того, что заведомо есть, доверять ей на
 * настоящем спектре нельзя.
 */
class SpectrumUnmixTest {

    private fun load(name: String) = SpectraFixtures.load(name)

    private val thorium: SpectrumTemplate by lazy {
        val (counts, calibration) = load("th232-source.csv")
        SpectrumTemplate(
            name = "Th-232",
            counts = counts,
            calibration = calibration,
            seconds = 27_714L,
            resolution662 = 0.084f,
            deviceName = "RadiaCode",
        )
    }

    private val background: Pair<List<Int>, EnergyCalibration> by lazy { load("background-71h.csv") }

    @Test
    fun `шаблон приводится к шкале прибора без потери площади`() {
        val target = background.second
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(
                template = thorium,
                targetCalibration = target,
                targetChannels = background.first.size,
                targetResolution662 = 0.084f,
            ),
        )
        // Перекладка переносит счёт, а не создаёт его: расхождение допустимо
        // только на краях шкалы, где часть каналов не перекрывается.
        val before = thorium.totalCounts.toDouble()
        val after = adapted.sum()
        assertTrue(after in 0.9 * before..1.01 * before, "было $before, стало $after")
    }

    @Test
    fun `прибор с лучшим разрешением не получает чужой шаблон`() {
        // Сузить измеренную линию нечем: сведения о её форме утеряны при
        // измерении. Отказ здесь честнее натянутой формы.
        assertNull(
            SpectrumTemplate.adapt(
                template = thorium,
                targetCalibration = background.second,
                targetChannels = background.first.size,
                targetResolution662 = 0.05f,
            ),
        )
    }

    @Test
    fun `известная доля восстанавливается по реальной форме`() {
        val target = background.second
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, target, background.first.size, 0.084f),
        )
        val share = 0.30
        val random = Random(20260821)
        val measured = adapted.map { poisson(it * share, random) }

        val result = assertNotNull(
            SpectrumUnmix.of(
                counts = measured,
                calibration = target,
                resolution662 = 0.084f,
                templates = listOf(thorium),
                fitScale = false,
            ),
        )
        val recovered = result.components.single().scale
        assertEquals(share, recovered, 0.02, "доля восстановлена как $recovered вместо $share")
        assertTrue(result.consistent, "модель верна, а согласия нет: ${result.cashDeviation}σ")
        assertTrue(result.explainedFraction > 0.9, "объяснено ${result.explainedFraction}")
    }

    @Test
    fun `подгонка находит уехавшую шкалу прибора`() {
        // На этом приборе линия K-40 стоит примерно на 1432 кэВ вместо 1460,8 —
        // около −2 %. Если шкалу не подбирать, подгонка компенсирует сдвиг
        // чужими компонентами.
        val target = background.second
        val drifted = EnergyCalibration(
            a0 = target.a0 * 0.98f,
            a1 = target.a1 * 0.98f,
            a2 = target.a2 * 0.98f,
        )
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, drifted, background.first.size, 0.084f),
        )
        val random = Random(7)
        val measured = adapted.map { poisson(it * 0.5, random) }

        val result = assertNotNull(
            SpectrumUnmix.of(
                counts = measured,
                calibration = target,
                resolution662 = 0.084f,
                templates = listOf(thorium),
            ),
        )
        assertTrue(
            abs(result.gain - 0.98) <= 0.006,
            "усиление найдено как ${result.gain} вместо 0,98",
        )
    }

    @Test
    fun `неполный состав виден по статистике, а не по доле объяснённого`() {
        // Фон прибора — это калий, урановый и ториевый ряды вместе. Разложение
        // ОДНИМ ториевым шаблоном обязано быть отвергнуто статистикой, хотя
        // «доля объяснённого» при этом остаётся высокой: широкая форма
        // поглощает остаток. Ради этого различия статистика Кэша и заведена.
        val (counts, calibration) = background
        val result = assertNotNull(
            SpectrumUnmix.of(
                counts = counts,
                calibration = calibration,
                resolution662 = 0.084f,
                templates = listOf(thorium),
            ),
        )
        assertTrue(
            !result.consistent,
            "неполный состав признан согласованным: ${result.cashDeviation}σ",
        )
        assertTrue(
            result.explainedFraction > 0.5,
            "доля объяснённого ${result.explainedFraction} — тест теряет смысл",
        )
    }

    @Test
    fun `короткий шаблон честно теряет точность доли`() {
        // Тот же измеренный спектр раскладывается дважды: полным шаблоном
        // (7,7 ч, 3,1 млн импульсов) и им же, прореженным в 30 раз — это
        // 15-минутное накопление. Форма у них одна, доля обязана совпасть, а
        // неопределённость доли — нет: у прореженного в канале верхней части
        // шкалы единицы импульсов, и этот шум входит в ответ.
        val target = background.second
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, target, background.first.size, 0.084f),
        )
        val measured = Random(20260821).let { random -> adapted.map { poisson(it * 0.30, random) } }
        val short = thin(thorium, 30, Random(11))

        val full = assertNotNull(unmix(measured, target, thorium)).components.single()
        val poor = assertNotNull(unmix(measured, target, short)).components.single()

        // Множитель у прореженного шаблона в 30 раз больше: сравнивать можно
        // только относительные величины.
        val fullNoise = full.sigmaTemplate / full.scale
        val poorNoise = poor.sigmaTemplate / poor.scale
        assertTrue(
            poorNoise > 3.0 * fullNoise,
            "шум короткого шаблона $poorNoise против $fullNoise у полного",
        )
        val total = measured.sumOf { it.toDouble() }
        val fullShare = full.counts / total
        val poorShare = poor.counts / total
        val tolerance = 3.0 * (poor.sigma / poor.scale + full.sigma / full.scale)
        assertTrue(
            abs(poorShare - fullShare) <= tolerance,
            "доли разошлись: $poorShare против $fullShare при допуске $tolerance",
        )
    }

    @Test
    fun `у длинного шаблона главный шум — шум данных`() {
        // В шаблоне 3,1 млн импульсов, в измерении — около 156 тыс.: данных в
        // 20 раз меньше. Пока это так, полную σ обязан задавать шум данных,
        // иначе разложение занижает точность длинного накопления.
        val target = background.second
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, target, background.first.size, 0.084f),
        )
        val measured = Random(4).let { random -> adapted.map { poisson(it * 0.05, random) } }

        val component = assertNotNull(unmix(measured, target, thorium)).components.single()
        assertTrue(
            component.sigmaTemplate < 0.5 * component.sigmaData,
            "шаблонный шум ${component.sigmaTemplate} против данных ${component.sigmaData}",
        )
        assertEquals(
            kotlin.math.sqrt(
                component.sigmaData * component.sigmaData +
                    component.sigmaTemplate * component.sigmaTemplate,
            ),
            component.sigma,
            1e-12,
        )
    }

    @Test
    fun `одинаковый спектр даёт одинаковую неопределённость`() {
        // Экран пересчитывает разложение при каждом обновлении спектра.
        // Плавающее зерно бутстрэпа заставило бы σ мигать от вызова к вызову.
        val target = background.second
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, target, background.first.size, 0.084f),
        )
        val measured = Random(3).let { random -> adapted.map { poisson(it * 0.20, random) } }

        val first = assertNotNull(unmix(measured, target, thorium)).components.single()
        val second = assertNotNull(unmix(measured, target, thorium)).components.single()
        assertEquals(first.sigmaTemplate, second.sigmaTemplate, 0.0)
    }

    @Test
    fun `короткий шаблон поднимает предел обнаружения`() {
        // Тот же спектр раскладывается двумя формами: фон прибора (71 ч) и линия
        // Tl-208 из измеренного ториевого спектра. Линия берётся вместо всего
        // ториевого шаблона потому, что предел обнаружения — вопрос об ОТДЕЛЬНОЙ
        // форме: широкий ториевый континуум частично повторяет фон, и «нулевая»
        // доля у него определена самой подгонкой, а не порогом.
        //
        // Шаблон линии прореживается в 30 раз — 15-минутное накопление вместо
        // 7,7-часового. Доля обязана остаться той же, а предел — вырасти: при
        // нулевой доле разброс проекции задан в том числе шумом самого шаблона.
        val target = background.second
        val measured = lineSpectrum(share = 0.015, seed = 20260821)

        val full = unmixPair(measured, target, line).components[1]
        val short = unmixPair(measured, target, thin(line, 30, Random(11))).components[1]

        // Множитель короткого шаблона в 30 раз больше — сравниваются пороги,
        // приведённые к своему множителю.
        val fullLimit = full.criticalScale / full.scale
        val shortLimit = short.criticalScale / short.scale
        assertTrue(
            shortLimit > 1.8 * fullLimit,
            "предел короткого шаблона $shortLimit против $fullLimit у полного",
        )
        val tolerance = 3.0 * (short.sigma / short.scale + full.sigma / full.scale)
        assertTrue(
            abs(short.scale / 30.0 - full.scale) <= tolerance * full.scale,
            "доли разошлись: ${short.scale / 30.0} против ${full.scale}",
        )
        // Тот же спектр — тот же порог: экран пересчитывает разложение постоянно.
        val again = unmixPair(measured, target, thin(line, 30, Random(11))).components[1]
        assertEquals(short.criticalScale, again.criticalScale, 0.0)
    }

    @Test
    fun `слабая доля не держится на коротком шаблоне`() {
        // Доля подобрана у самого предела: линия даёт около восьми импульсов.
        // Полный шаблон её удерживает, короткий — нет, причём ОБА множителя лежат
        // между двумя порогами: у короткого шаблона он даже больше, и по прежнему
        // порогу (только по данным) он тоже был бы «обнаружен». Разницу делает
        // именно поднявшийся порог, а не подгонка.
        val target = background.second
        val measured = lineSpectrum(share = 0.0008, seed = 38)

        val full = unmixPair(measured, target, line).components[1]
        val short = unmixPair(measured, target, thin(line, 30, Random(11))).components[1]

        assertTrue(full.detected, "доля ${full.scale} не найдена при пороге ${full.criticalScale}")
        assertTrue(
            !short.detected,
            "доля ${short.scale} признана найденной при пороге ${short.criticalScale}",
        )
        assertTrue(
            short.scale / 30.0 > full.criticalScale,
            "множитель ${short.scale / 30.0} и без шума шаблона не дотягивал бы " +
                "до порога ${full.criticalScale} — тест проверял бы подгонку",
        )
        val tolerance = 3.0 * (short.sigma / 30.0 + full.sigma)
        assertTrue(
            abs(short.scale / 30.0 - full.scale) <= tolerance,
            "доли разошлись: ${short.scale / 30.0} против ${full.scale}",
        )
    }

    @Test
    fun `у единственной формы предел не меняется`() {
        // Модели «без этой формы» не существует: сравнивать не с чем, и вклад
        // шаблонов в предел равен нулю — так же, как вклад данных.
        val target = background.second
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, target, background.first.size, 0.084f),
        )
        val measured = Random(20260821).let { random -> adapted.map { poisson(it * 0.30, random) } }

        val component = assertNotNull(unmix(measured, target, thorium)).components.single()
        assertEquals(0.0, component.criticalScale, 0.0)
        // Реплики при этом строились: нулевой предел — не пропущенный бутстрэп.
        assertTrue(component.sigmaTemplate > 0.0, "бутстрэп шаблона не отработал")
    }

    /**
     * Форма линии Tl-208: из измеренного ториевого спектра оставлена область
     * 2400–2800 кэВ. Это по-прежнему реальная форма прибора, но почти
     * ортогональная фону — на ней вопрос «есть или нет» решает порог, а не
     * вырождение с фоновым континуумом.
     */
    private val line: SpectrumTemplate by lazy {
        val calibration = thorium.calibration
        thorium.copy(
            name = "Tl-208",
            counts = thorium.counts.mapIndexed { channel, value ->
                val energy = calibration.a0 + calibration.a1 * channel +
                    calibration.a2 * channel * channel
                if (energy in 2400f..2800f) value else 0
            },
        )
    }

    /** «Измерение»: фон прибора с добавкой [share] от формы линии. */
    private fun lineSpectrum(share: Double, seed: Int): List<Int> {
        val target = background.second
        val channels = background.first.size
        val backgroundShape = assertNotNull(
            SpectrumTemplate.adapt(backgroundTemplate, target, channels, 0.084f),
        )
        val lineShape = assertNotNull(SpectrumTemplate.adapt(line, target, channels, 0.084f))
        val random = Random(seed)
        return backgroundShape.indices.map {
            poisson(backgroundShape[it] * backgroundShare + lineShape[it] * share, random)
        }
    }

    /**
     * Сколько фона в «измерении»: 0,05 от 71-часового накопления — около
     * 3,5 часов, обычная экспозиция для разбора состава.
     */
    private val backgroundShare = 0.05

    /** Собственный фон прибора как шаблон: 71 ч, 6,3 млн импульсов. */
    private val backgroundTemplate: SpectrumTemplate by lazy {
        SpectrumTemplate(
            name = "фон",
            counts = background.first,
            calibration = background.second,
            seconds = 255_600L,
            resolution662 = 0.084f,
            deviceName = "RadiaCode",
        )
    }

    private fun unmixPair(
        measured: List<Int>,
        calibration: EnergyCalibration,
        second: SpectrumTemplate,
    ) = assertNotNull(
        SpectrumUnmix.of(
            counts = measured,
            calibration = calibration,
            resolution662 = 0.084f,
            templates = listOf(backgroundTemplate, second),
            fitScale = false,
        ),
    )

    private fun unmix(
        measured: List<Int>,
        calibration: EnergyCalibration,
        template: SpectrumTemplate,
    ) = SpectrumUnmix.of(
        counts = measured,
        calibration = calibration,
        resolution662 = 0.084f,
        templates = listOf(template),
        fitScale = false,
    )

    /**
     * Прореживание шаблона в [factor] раз: пуассоновский счёт с уменьшенным
     * средним и во столько же раз меньшее время накопления — то же измерение,
     * только короче.
     */
    private fun thin(template: SpectrumTemplate, factor: Int, random: Random) = template.copy(
        counts = template.counts.map { poisson(it.toDouble() / factor, random) },
        seconds = template.seconds / factor,
    )

    /** Пуассоновский отсчёт: для больших средних — гауссово приближение. */
    private fun poisson(mean: Double, random: Random): Int {
        if (mean <= 0.0) return 0
        if (mean > 30.0) {
            val value = mean + kotlin.math.sqrt(mean) * gaussian(random)
            return value.toInt().coerceAtLeast(0)
        }
        var product = 1.0
        var count = 0
        val limit = kotlin.math.exp(-mean)
        while (true) {
            product *= random.nextDouble()
            if (product <= limit) return count
            count++
            if (count > 1000) return count
        }
    }

    private fun gaussian(random: Random): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) *
            kotlin.math.cos(2.0 * Math.PI * u2)
    }
}
