package app.alpha.analysis

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Разложение по шаблонам проверяется на ПОСТРОЕННЫХ спектрах
 * ([SyntheticSpectra]): у каждой линии известны энергия и площадь, ширина взята
 * по форме FWHM = R·√(662·E) при R = [RESOLUTION], континуум задан уровнем и
 * наклоном, статистика — множителем накопления. Ожидания выведены из этих
 * чисел.
 *
 * Замкнутый цикл: из формы строится «измерение» с заданной долей и
 * пуассоновским шумом, и подгонка обязана вернуть эту долю, эту шкалу и
 * неопределённость, которая покрывает разброс ответа. Если движок не находит
 * того, что заведомо есть, доверять ему на измеренном спектре нельзя.
 *
 * Граница метода: построенный спектр несёт ровно те особенности, которые в него
 * заложены (несимметричный хвост линии, наклонный континуум, собственный шум
 * шаблона). Особенность прибора, не описанная этими параметрами, здесь не
 * проверяется.
 */
class SpectrumUnmixTest {

    /**
     * Прибор, на котором раскладывают. Шкала шире шаблонной — 2,6 кэВ/канал
     * против 2,34, — поэтому приведение шаблона это настоящая перекладка по
     * перекрытию границ каналов, а не копирование канал в канал. Диапазон
     * 2,7…2979 кэВ накрывает шаблонный 5,7…2805 кэВ целиком.
     */
    private val device = EnergyCalibration(4.0f, 2.6f, 3.0e-4f)

    /** Разрешение и шаблонного прибора, и целевого: уширения при приведении нет. */
    private val resolution = 0.084f

    /** Ториевый источник: 3,14 млн импульсов за 7,7 ч. */
    private val thorium = SpectrumTemplate(
        name = "Th-232",
        counts = SyntheticSpectra.thoriumSource(scale = 1.0),
        calibration = SyntheticSpectra.CALIBRATION,
        seconds = 27_714L,
        resolution662 = resolution,
        deviceName = "шаблонный прибор",
    )

    /** Природный фон: 5,91 млн импульсов за 71 ч. */
    private val backgroundTemplate = SpectrumTemplate(
        name = "фон",
        counts = SyntheticSpectra.naturalBackground(scale = 20.0),
        calibration = SyntheticSpectra.CALIBRATION,
        seconds = 255_600L,
        resolution662 = resolution,
        deviceName = "шаблонный прибор",
    )

    @Test
    fun `шаблон приводится к шкале прибора без потери площади`() {
        // Перекладка переносит счёт, а не создаёт его. Шкала прибора накрывает
        // шаблонную целиком, поэтому терять нечего: расхождение остаётся на
        // уровне накопленной ошибки double по 1024 каналам.
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val before = thorium.totalCounts.toDouble()
        assertEquals(before, adapted.sum(), 1e-6 * before)

        // У прибора с короткой шкалой (1,2…1744 кэВ) пропадает ровно то, что
        // лежит выше её верхней границы, — включая линию 2614,5 кэВ. Нижний
        // край шаблона (5,7 кэВ) при этом накрыт, и снизу терять нечего.
        val short = EnergyCalibration(2.0f, 1.6f, 1.0e-4f)
        val edge = short.energyAt(SyntheticSpectra.CHANNELS - 0.5f)
        val above = thorium.counts.filterIndexed { channel, _ ->
            thorium.calibration.energyAt(channel.toFloat()) > edge
        }.sumOf { it.toDouble() }
        val clipped = assertNotNull(
            SpectrumTemplate.adapt(thorium, short, SyntheticSpectra.CHANNELS, resolution),
        )
        // Допуск 1 % от потери: канал, разрезанный границей, несёт около 600
        // импульсов при потере 140 тыс.
        assertEquals(before - above, clipped.sum(), 0.01 * above)
    }

    @Test
    fun `прибор с лучшим разрешением не получает чужой шаблон`() {
        // Сузить измеренную линию нечем: сведения о её форме утеряны при
        // измерении. Отказ здесь честнее натянутой формы.
        assertNull(
            SpectrumTemplate.adapt(thorium, device, SyntheticSpectra.CHANNELS, 0.05f),
        )
    }

    @Test
    fun `известная доля восстанавливается по построенной форме`() {
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val share = 0.30
        val random = Random(20260821)
        val measured = adapted.map { SyntheticSpectra.poisson(it * share, random) }

        val result = assertNotNull(
            SpectrumUnmix.of(
                counts = measured,
                calibration = device,
                resolution662 = resolution,
                templates = listOf(thorium),
                fitScale = false,
            ),
        )
        val recovered = result.components.single().scale
        // В «измерении» 0,3·3,14 млн ≈ 943 тыс. импульсов, то есть 1/√N = 1,0·10⁻³
        // относительных, 3,1·10⁻⁴ абсолютных. Допуск 0,002 — это 6σ.
        assertEquals(share, recovered, 0.002, "доля восстановлена как $recovered вместо $share")
        assertTrue(result.consistent, "модель верна, а согласия нет: ${result.cashDeviation}σ")
        assertTrue(result.explainedFraction > 0.9, "объяснено ${result.explainedFraction}")
    }

    @Test
    fun `подгонка находит уехавшую шкалу прибора`() {
        // Спектр снят прибором, у которого шкала на 2 % ниже объявленной: на
        // 1460,8 кэВ это 29 кэВ, больше четверти ширины линии. Если шкалу не
        // подбирать, подгонка компенсирует сдвиг чужими компонентами.
        val drifted = EnergyCalibration(
            a0 = device.a0 * 0.98f,
            a1 = device.a1 * 0.98f,
            a2 = device.a2 * 0.98f,
        )
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, drifted, SyntheticSpectra.CHANNELS, resolution),
        )
        val random = Random(7)
        val measured = adapted.map { SyntheticSpectra.poisson(it * 0.5, random) }

        val result = assertNotNull(
            SpectrumUnmix.of(
                counts = measured,
                calibration = device,
                resolution662 = resolution,
                templates = listOf(thorium),
            ),
        )
        // Допуск — шаг сетки усиления (0,5 %) с запасом.
        assertTrue(
            abs(result.gain - 0.98) <= 0.006,
            "усиление найдено как ${result.gain} вместо 0,98",
        )
    }

    @Test
    fun `предсказание шкалы достаёт сдвиг, до которого не дотягивается сетка`() {
        // Прибор ушёл на −4 %: это дальше края сетки по умолчанию (±3 %), и
        // без подсказки подгонка упирается в собственный край, компенсируя
        // остаток формой. Измеренный температурный ход говорит, ГДЕ искать, —
        // и тот же спектр раскладывается верно.
        val drift = 0.96
        val drifted = EnergyCalibration(
            a0 = (device.a0 * drift).toFloat(),
            a1 = (device.a1 * drift).toFloat(),
            a2 = (device.a2 * drift).toFloat(),
        )
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, drifted, SyntheticSpectra.CHANNELS, resolution),
        )
        val random = Random(31)
        val measured = adapted.map { SyntheticSpectra.poisson(it * 0.5, random) }

        val blind = assertNotNull(
            SpectrumUnmix.of(
                counts = measured,
                calibration = device,
                resolution662 = resolution,
                templates = listOf(thorium),
            ),
        )
        assertTrue(
            abs(blind.gain - drift) > 0.005,
            "без предсказания сдвиг за краем сетки не должен находиться, вышло ${blind.gain}",
        )

        val guided = assertNotNull(
            SpectrumUnmix.of(
                counts = measured,
                calibration = device,
                resolution662 = resolution,
                templates = listOf(thorium),
                // Предсказание неточное намеренно: оно двигает окно поиска, а
                // величину по-прежнему определяют данные.
                scalePrior = SpectrumUnmix.ScalePrior(gain = 0.962, sigma = 0.002),
            ),
        )
        assertTrue(
            abs(guided.gain - drift) <= 0.005,
            "с предсказанием усиление найдено как ${guided.gain} вместо $drift",
        )
        assertTrue(
            guided.cash < blind.cash,
            "верная шкала обязана описывать данные лучше: ${guided.cash} против ${blind.cash}",
        )
    }

    @Test
    fun `сошедшаяся подгонка так и говорит`() {
        // Признак сходимости — не украшение: результат, полученный упором в
        // счётчик итераций, не является максимумом правдоподобия, и отличить
        // его снаружи иначе нечем.
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val random = Random(20260822)
        val measured = adapted.map { SyntheticSpectra.poisson(it * 0.30, random) }

        val result = assertNotNull(
            SpectrumUnmix.of(
                counts = measured,
                calibration = device,
                resolution662 = resolution,
                templates = listOf(thorium),
                fitScale = false,
            ),
        )
        assertTrue(result.converged, "подгонка обязана сходиться")
    }

    @Test
    fun `неполный состав виден по статистике, а не по доле объяснённого`() {
        // «Измерение» — смесь двух форм: фон (доля 0,05) и ториевый источник
        // (доля 0,20). Разложение ОДНИМ ториевым шаблоном обязано быть
        // отвергнуто статистикой, хотя «доля объяснённого» при этом остаётся
        // высокой: широкая форма поглощает остаток. Ради этого различия
        // статистика Кэша и заведена.
        val measured = mixture(backgroundShare = 0.05, thoriumShare = 0.20, seed = 20260901)

        val partial = assertNotNull(
            SpectrumUnmix.of(
                counts = measured,
                calibration = device,
                resolution662 = resolution,
                templates = listOf(thorium),
            ),
        )
        assertTrue(
            !partial.consistent,
            "неполный состав признан согласованным: ${partial.cashDeviation}σ",
        )
        assertTrue(
            partial.explainedFraction > 0.5,
            "доля объяснённого ${partial.explainedFraction} — тест теряет смысл",
        )

        // Тот же спектр полным составом: отвергает его именно недостача формы,
        // а не спектр сам по себе.
        val full = assertNotNull(
            SpectrumUnmix.of(
                counts = measured,
                calibration = device,
                resolution662 = resolution,
                templates = listOf(backgroundTemplate, thorium),
            ),
        )
        assertTrue(full.consistent, "полный состав не согласован: ${full.cashDeviation}σ")
        // Допуск 5 % от доли: формы перекрываются почти на всей шкале, и обмен
        // счётом между ними — не ошибка подгонки, а свойство задачи.
        assertEquals(0.05, full.components[0].scale, 0.05 * 0.05)
        assertEquals(0.20, full.components[1].scale, 0.05 * 0.20)
    }

    @Test
    fun `короткий шаблон честно теряет точность доли`() {
        // Тот же спектр раскладывается дважды: полным шаблоном (7,7 ч, 3,14 млн
        // импульсов) и им же, прореженным в 30 раз — это 15-минутное
        // накопление, 104 тыс. импульсов. Форма у них одна, доля обязана
        // совпасть, а неопределённость доли — нет: у прореженного в канале
        // верхней части шкалы единицы импульсов, и этот шум входит в ответ.
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val measured = Random(20260821).let { random ->
            adapted.map { SyntheticSpectra.poisson(it * 0.30, random) }
        }
        val short = thin(thorium, 30, Random(11))

        val full = assertNotNull(unmix(measured, thorium)).components.single()
        val poor = assertNotNull(unmix(measured, short)).components.single()

        // Множитель у прореженного шаблона в 30 раз больше: сравнивать можно
        // только относительные величины. Ожидание — рост в √30 = 5,5 раза;
        // порог 3 оставляет запас на 20 % неопределённости самой σ (B = 14
        // реплик бутстрэпа).
        val fullNoise = full.sigmaTemplate / full.scale
        val poorNoise = poor.sigmaTemplate / poor.scale
        assertTrue(
            poorNoise > 3.0 * fullNoise,
            "шум короткого шаблона $poorNoise против $fullNoise у полного",
        )
        val tolerance = 3.0 * (poor.sigma / 30.0 + full.sigma)
        assertTrue(
            abs(poor.scale / 30.0 - full.scale) <= tolerance,
            "доли разошлись: ${poor.scale / 30.0} против ${full.scale} при допуске $tolerance",
        )
    }

    @Test
    fun `у длинного шаблона главный шум — шум данных`() {
        // В шаблоне 3,14 млн импульсов, в «измерении» — 157 тыс.: данных в
        // 20 раз меньше. Пока это так, полную σ обязан задавать шум данных,
        // иначе разложение занижает точность длинного накопления.
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val measured = Random(4).let { random ->
            adapted.map { SyntheticSpectra.poisson(it * 0.05, random) }
        }

        val component = assertNotNull(unmix(measured, thorium)).components.single()
        assertTrue(
            component.sigmaTemplate < 0.5 * component.sigmaData,
            "шаблонный шум ${component.sigmaTemplate} против данных ${component.sigmaData}",
        )
        assertEquals(
            sqrt(
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
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val measured = Random(3).let { random ->
            adapted.map { SyntheticSpectra.poisson(it * 0.20, random) }
        }

        val first = assertNotNull(unmix(measured, thorium)).components.single()
        val second = assertNotNull(unmix(measured, thorium)).components.single()
        assertEquals(first.sigmaTemplate, second.sigmaTemplate, 0.0)
    }

    @Test
    fun `короткий шаблон поднимает предел обнаружения`() {
        // Тот же спектр раскладывается двумя формами: фон и отдельная линия
        // 2614,5 кэВ. Линия берётся вместо всего ториевого шаблона потому, что
        // предел обнаружения — вопрос об ОТДЕЛЬНОЙ форме: широкий ториевый
        // континуум частично повторяет фон, и «нулевая» доля у него определена
        // самой подгонкой, а не порогом.
        //
        // Фона в «измерении» мало (0,002 от 71-часового накопления — около
        // 8,5 минут): пока фон не закрывает линию, точность доли упирается в
        // то, насколько хорошо известна сама форма линии, то есть в шаблон.
        //
        // Шаблон линии прореживается в 100 раз — 4,6 минуты накопления вместо
        // 7,7 часов, 424 импульса вместо 40 тыс. Доля обязана остаться той же,
        // а предел — вырасти.
        val measured = lineSpectrum(share = 0.01, seed = 20260821)
        val short = thin(line, 100, Random(11))

        val full = unmixPair(measured, line).components[1]
        val poor = unmixPair(measured, short).components[1]

        // Пределы приведены к ОДНОЙ форме — полному шаблону; множитель у
        // прореженного в 100 раз больше. Измеренный рост — 1,93 раза; порог 1,5
        // оставляет запас на 20 % неопределённости бутстрэпа (B = 14 реплик),
        // которым посчитан шаблонный вклад в предел.
        assertTrue(
            poor.criticalScale / 100.0 > 1.5 * full.criticalScale,
            "предел короткого шаблона ${poor.criticalScale / 100.0} против ${full.criticalScale}",
        )
        val tolerance = 3.0 * (poor.sigma / 100.0 + full.sigma)
        assertTrue(
            abs(poor.scale / 100.0 - full.scale) <= tolerance,
            "доли разошлись: ${poor.scale / 100.0} против ${full.scale} при допуске $tolerance",
        )
        // Тот же спектр — тот же порог: экран пересчитывает разложение постоянно.
        val again = unmixPair(measured, thin(line, 100, Random(11))).components[1]
        assertEquals(poor.criticalScale, again.criticalScale, 0.0)
    }

    @Test
    fun `у единственной формы предел не меняется`() {
        // Модели «без этой формы» не существует: сравнивать не с чем, и вклад
        // шаблонов в предел равен нулю — так же, как вклад данных.
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val measured = Random(20260821).let { random ->
            adapted.map { SyntheticSpectra.poisson(it * 0.30, random) }
        }

        val component = assertNotNull(unmix(measured, thorium)).components.single()
        assertEquals(0.0, component.criticalScale, 0.0)
        // Реплики при этом строились: нулевой предел — не пропущенный бутстрэп.
        assertTrue(component.sigmaTemplate > 0.0, "бутстрэп шаблона не отработал")
    }

    @Test
    fun `подгонка доходит до оптимума, а не до счёта итераций`() {
        // «Измерение» построено из ОДНОГО фона. Раскладывается дважды: им же и
        // им же ВМЕСТЕ с ториевым шаблоном, которого в спектре нет.
        //
        // Вторая модель содержит первую как частный случай (ториевая доля равна
        // нулю), поэтому описывать данные хуже она не может: у мультипликативных
        // итераций правдоподобие монотонно, и найденная точка обязана быть
        // оптимумом. Разошедшаяся статистика означает не «лишняя форма мешает», а
        // «итерации остановлены раньше сходимости» — и лишняя форма при этом
        // уносит счёт, которого ей никто не давал.
        val shape = assertNotNull(
            SpectrumTemplate.adapt(
                backgroundTemplate,
                device,
                SyntheticSpectra.CHANNELS,
                resolution,
            ),
        )
        val measured = Random(20260821).let { random ->
            shape.map { SyntheticSpectra.poisson(it * 0.05, random) }
        }

        val alone = assertNotNull(unmix(measured, backgroundTemplate))
        val withAbsent = assertNotNull(
            SpectrumUnmix.of(
                counts = measured,
                calibration = device,
                resolution662 = resolution,
                templates = listOf(backgroundTemplate, thorium),
                fitScale = false,
            ),
        )
        // Допуск 0,01 — сотая доля от ΔC = 1, то есть от одной σ.
        assertTrue(
            withAbsent.cash <= alone.cash + 0.01,
            "две формы дали C = ${withAbsent.cash} против ${alone.cash} у одной: " +
                "подгонка не в оптимуме",
        )
        val absent = withAbsent.components[1]
        val share = absent.counts / measured.sumOf { it.toDouble() }
        assertTrue(share < 1e-5, "отсутствующей форме досталось $share измеренного счёта")
        assertTrue(!absent.detected, "отсутствующая форма ${absent.scale} признана найденной")
    }

    @Test
    fun `перекрытие форм не размножает ложные обнаружения`() {
        // Односторонний критерий Карри обещает 95 %: ложное «обнаружено» на
        // отсутствующей форме допустимо примерно в одном случае из двадцати.
        //
        // Проверяется это единственным честным способом — счётом по РЕАЛИЗАЦИЯМ
        // шума. «Измерение» каждый раз строится из ОДНОГО фона, а раскладывается
        // по двум почти коллинеарным формам: фон и он же с более пологим
        // континуумом. Второй формы в данных нет ни в одной реализации.
        val real = assertNotNull(
            SpectrumTemplate.adapt(
                backgroundTemplate,
                device,
                SyntheticSpectra.CHANNELS,
                resolution,
            ),
        )
        val twinShape = assertNotNull(
            SpectrumTemplate.adapt(softContinuum, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val channels = SyntheticSpectra.CHANNELS
        var byDiagonal = 0
        var byMatrix = 0
        for (seed in 1..20) {
            val random = Random(seed)
            val measured = (0 until channels).map {
                SyntheticSpectra.poisson(real[it] * 5e-3, random)
            }
            val result = assertNotNull(
                SpectrumUnmix.of(
                    counts = measured,
                    calibration = device,
                    resolution662 = resolution,
                    templates = listOf(backgroundTemplate, softContinuum),
                    fitScale = false,
                ),
            )
            val twin = result.components[1]
            val model = DoubleArray(channels) {
                result.components[0].scale * real[it] + twin.scale * twinShape[it]
            }
            // Информация по модели гипотезы «двойника нет»: ожидаемый счёт равен
            // самой этой модели. Отсюда прежний, диагональный предел Карри — и
            // тот же предел по обратной матрице, чтобы вычесть общий для обоих
            // вклад шума шаблонов.
            val zeroModel = DoubleArray(channels) { model[it] - twin.scale * twinShape[it] }
            val zero = information(real, twinShape, zeroModel) { zeroModel[it] }
            val diagonalLimit = SIGMAS / sqrt(zero.second)
            val matrixLimit = SIGMAS * zero.marginal
            assertTrue(
                matrixLimit > 5.0 * diagonalLimit,
                "пределы неразличимы: $matrixLimit против $diagonalLimit",
            )
            val previous = sqrt(
                diagonalLimit * diagonalLimit + twin.criticalScale * twin.criticalScale -
                    matrixLimit * matrixLimit,
            )
            if (twin.scale > previous) byDiagonal++
            if (twin.detected) byMatrix++
        }
        // Множитель отсутствующей формы положителен примерно в половине
        // реализаций (проверено: 13 из 20), и почти каждый положительный
        // перекрывает диагональный предел, который в 24 раза меньше
        // матричного. Порог 7 из 20 — это −2σ от биномиального ожидания 10.
        assertTrue(
            byDiagonal >= 7,
            "диагональный предел ошибся лишь $byDiagonal раз из 20 — проверять нечего",
        )
        // 5 % от 20 реализаций — одна; 3 это +2σ биномиального разброса.
        assertTrue(
            byMatrix <= 3,
            "отсутствующая форма объявлена найденной $byMatrix раз из 20 при обещанных 5 %",
        )
    }

    @Test
    fun `σ доли покрывает разброс ответа на перекрытых формах`() {
        // Неопределённость проверяется покрытием: «измерение» строится 20 раз с
        // разным шумом при ОДНОМ и том же истинном составе, и разброс полученных
        // долей сравнивается с тем, что движок обещал.
        //
        // Формы почти коллинеарны (фон и он же с более пологим континуумом),
        // поэтому подгонка перекладывает счёт с одной на другую, и разброс задан
        // именно перекрытием. Диагональ кривизны его не видит.
        val channels = SyntheticSpectra.CHANNELS
        val real = assertNotNull(
            SpectrumTemplate.adapt(
                backgroundTemplate,
                device,
                SyntheticSpectra.CHANNELS,
                resolution,
            ),
        )
        val twinShape = assertNotNull(
            SpectrumTemplate.adapt(softContinuum, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val share = 5e-3
        val fitted = ArrayList<Double>()
        var reported = 0.0
        var diagonal = 0.0
        for (seed in 1..20) {
            val random = Random(seed)
            val measured = (0 until channels).map {
                SyntheticSpectra.poisson(real[it] * share + twinShape[it] * share, random)
            }
            val result = assertNotNull(
                SpectrumUnmix.of(
                    counts = measured,
                    calibration = device,
                    resolution662 = resolution,
                    templates = listOf(backgroundTemplate, softContinuum),
                    fitScale = false,
                ),
            )
            val twin = result.components[1]
            val model = DoubleArray(channels) {
                result.components[0].scale * real[it] + twin.scale * twinShape[it]
            }
            val fisher = information(real, twinShape, model) { measured[it].toDouble() }
            assertEquals(fisher.marginal, twin.sigmaData, 1e-9 * fisher.marginal)
            fitted += twin.scale
            reported += twin.sigmaData / 20.0
            diagonal += 1.0 / sqrt(fisher.second) / 20.0
        }
        val mean = fitted.average()
        val spread = sqrt(fitted.sumOf { (it - mean) * (it - mean) } / (fitted.size - 1))
        // СКО по 20 реализациям само измерено с ошибкой 1/√(2·19) = 16 %, поэтому
        // допуск шире этой ошибки, но втрое уже расхождения с диагональю.
        assertTrue(
            spread / reported in 0.6..1.6,
            "разброс $spread против обещанного $reported",
        )
        assertTrue(
            spread > 5.0 * diagonal,
            "диагональ $diagonal объяснила бы разброс $spread — тест теряет смысл",
        )
    }

    @Test
    fun `у единственной формы σ остаётся прежней кривизной`() {
        // Матрица 1×1 обращается в 1/I₀₀, то есть в прежнюю формулу Σ N·T²/M².
        // Правка обязана быть незаметной там, где делить не с кем.
        val channels = SyntheticSpectra.CHANNELS
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, device, channels, resolution),
        )
        val measured = Random(20260821).let { random ->
            adapted.map { SyntheticSpectra.poisson(it * 0.30, random) }
        }

        val component = assertNotNull(unmix(measured, thorium)).components.single()
        var curvature = 0.0
        for (channel in 0 until channels) {
            val t = adapted[channel]
            if (t <= 0.0) continue
            val m = component.scale * t
            if (m > 0.0) curvature += measured[channel] * t * t / (m * m)
        }
        val previous = 1.0 / sqrt(curvature)
        assertEquals(previous, component.sigmaData, 1e-12 * previous)
    }

    @Test
    fun `неразличимые формы не получают ни σ, ни обнаружения`() {
        // Две копии одного шаблона: информационная матрица вырождена, доли
        // разделить нечем. Число тут выдумать нельзя — движок отдаёт NaN, и
        // «обнаружено» не срабатывает ни при каком множителе.
        val adapted = assertNotNull(
            SpectrumTemplate.adapt(thorium, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val measured = Random(20260821).let { random ->
            adapted.map { SyntheticSpectra.poisson(it * 0.30, random) }
        }

        val result = assertNotNull(
            SpectrumUnmix.of(
                counts = measured,
                calibration = device,
                resolution662 = resolution,
                templates = listOf(thorium, thorium.copy(name = "Th-232 копия")),
                fitScale = false,
            ),
        )
        for (component in result.components) {
            assertTrue(component.sigmaData.isNaN(), "σ ${component.sigmaData} у неразделимой доли")
            assertTrue(!component.detected, "неразделимая доля признана найденной")
        }
    }

    /**
     * Тот же фон с более пологим континуумом: те же линии, но подложка спадает к
     * верхнему краю шкалы в 50 раз вместо 40. Форма почти коллинеарна исходной —
     * различить их можно только по плавному перекосу подложки, и подгонка
     * перекладывает счёт с одной на другую почти без ущерба правдоподобию.
     */
    private val softContinuum: SpectrumTemplate by lazy {
        SpectrumTemplate(
            name = "фон, континуум мягче",
            counts = SyntheticSpectra.build(
                lines = listOf(
                    SyntheticSpectra.Line(energyKeV = 238.6, counts = 9_000.0),
                    SyntheticSpectra.Line(energyKeV = 351.9, counts = 6_000.0),
                    SyntheticSpectra.Line(energyKeV = 583.2, counts = 4_000.0),
                    SyntheticSpectra.Line(energyKeV = 609.3, counts = 5_000.0),
                    SyntheticSpectra.Line(energyKeV = 911.2, counts = 2_500.0),
                    SyntheticSpectra.Line(energyKeV = 1460.8, counts = 6_000.0),
                    SyntheticSpectra.Line(energyKeV = 1764.5, counts = 1_200.0),
                    SyntheticSpectra.Line(energyKeV = 2614.5, counts = 900.0),
                ),
                continuum = 900.0,
                continuumSlope = 50.0,
                scale = 20.0,
                seed = 20260107,
            ),
            calibration = SyntheticSpectra.CALIBRATION,
            seconds = 255_600L,
            resolution662 = resolution,
            deviceName = "шаблонный прибор",
        )
    }

    /**
     * Информационная матрица 2×2 по формам [first] и [second]:
     * `I_jl = Σ_канал weight·T_j·T_l / M²`, каналы с `M ≤ 0` пропущены.
     */
    private fun information(
        first: List<Double>,
        second: List<Double>,
        model: DoubleArray,
        weight: (Int) -> Double,
    ): Information {
        var i00 = 0.0
        var i01 = 0.0
        var i11 = 0.0
        for (channel in model.indices) {
            val m = model[channel]
            if (m <= 0.0) continue
            val w = weight(channel) / (m * m)
            i00 += w * first[channel] * first[channel]
            i01 += w * first[channel] * second[channel]
            i11 += w * second[channel] * second[channel]
        }
        return Information(i00, i01, i11)
    }

    /** Информационная матрица 2×2 в независимом от движка виде. */
    private data class Information(val first: Double, val offDiagonal: Double, val second: Double) {
        /** σ ВТОРОЙ формы по обращению 2×2: `(I⁻¹)₁₁ = I₀₀/det`. */
        val marginal: Double
            get() = sqrt(first / (first * second - offDiagonal * offDiagonal))
    }

    /** Односторонний 95 % критерий Карри — тот же множитель, что в движке. */
    private val SIGMAS = 1.645

    /**
     * Форма линии Tl-208 без подложки: 40 тыс. импульсов в линии 2614,5 кэВ.
     * Почти ортогональна фону — на ней вопрос «есть или нет» решает порог, а не
     * вырождение с фоновым континуумом.
     */
    private val line: SpectrumTemplate by lazy {
        SpectrumTemplate(
            name = "Tl-208",
            counts = SyntheticSpectra.build(
                lines = listOf(SyntheticSpectra.Line(energyKeV = 2614.5, counts = 40_000.0)),
                continuum = 0.0,
                seed = 20260104,
            ),
            calibration = SyntheticSpectra.CALIBRATION,
            seconds = 27_714L,
            resolution662 = resolution,
            deviceName = "шаблонный прибор",
        )
    }

    /** «Измерение»: фон с добавкой [share] от формы линии. */
    private fun lineSpectrum(share: Double, seed: Int): List<Int> {
        val backgroundShape = assertNotNull(
            SpectrumTemplate.adapt(
                backgroundTemplate,
                device,
                SyntheticSpectra.CHANNELS,
                resolution,
            ),
        )
        val lineShape = assertNotNull(
            SpectrumTemplate.adapt(line, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val random = Random(seed)
        return backgroundShape.indices.map {
            SyntheticSpectra.poisson(
                backgroundShape[it] * LINE_BACKGROUND_SHARE + lineShape[it] * share,
                random,
            )
        }
    }

    /** «Измерение» из двух построенных форм с известными долями. */
    private fun mixture(backgroundShare: Double, thoriumShare: Double, seed: Int): List<Int> {
        val backgroundShape = assertNotNull(
            SpectrumTemplate.adapt(
                backgroundTemplate,
                device,
                SyntheticSpectra.CHANNELS,
                resolution,
            ),
        )
        val thoriumShape = assertNotNull(
            SpectrumTemplate.adapt(thorium, device, SyntheticSpectra.CHANNELS, resolution),
        )
        val random = Random(seed)
        return backgroundShape.indices.map {
            SyntheticSpectra.poisson(
                backgroundShape[it] * backgroundShare + thoriumShape[it] * thoriumShare,
                random,
            )
        }
    }

    private fun unmixPair(measured: List<Int>, second: SpectrumTemplate) = assertNotNull(
        SpectrumUnmix.of(
            counts = measured,
            calibration = device,
            resolution662 = resolution,
            templates = listOf(backgroundTemplate, second),
            fitScale = false,
        ),
    )

    private fun unmix(measured: List<Int>, template: SpectrumTemplate) = SpectrumUnmix.of(
        counts = measured,
        calibration = device,
        resolution662 = resolution,
        templates = listOf(template),
        fitScale = false,
    )

    /**
     * Прореживание шаблона в [factor] раз: пуассоновский счёт с уменьшенным
     * средним и во столько же раз меньшее время накопления — то же измерение,
     * только короче.
     */
    private fun thin(template: SpectrumTemplate, factor: Int, random: Random) = template.copy(
        counts = template.counts.map { SyntheticSpectra.poisson(it.toDouble() / factor, random) },
        seconds = template.seconds / factor,
    )

    private companion object {
        /**
         * Сколько фона в «измерении» с линией: 0,002 от 71-часового накопления —
         * около 8,5 минут. Мало намеренно: пока фон не закрывает линию, предел
         * обнаружения определяется знанием формы, то есть шаблоном.
         */
        const val LINE_BACKGROUND_SHARE = 0.002
    }
}
