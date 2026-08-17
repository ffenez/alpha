package app.alpha.data.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Файл, выгруженный ШТАТНЫМ приложением RadiaCode, — полевой ввод, а не наш
 * собственный экспорт: он приходит с чужими особенностями, и разбор обязан их
 * пережить.
 *
 * Фикстур повторяет структуру настоящей выгрузки (полевой случай импорта,
 * который у пользователя не работал): два спектра в одной записи (измерение и
 * `BackgroundEnergySpectrum`), имена в CDATA, дробное `LiveTime`, коэффициент
 * калибровки в научной записи, пустой `Comment`, хвост `PulseCollection` и
 * рваные отступы. Данные внутри синтетические: чужие метки и серийник в
 * репозиторий не кладутся.
 */
class RcXmlNativeExportTest {

    private fun fixture(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("native-app-export.xml"))
            .use { it.readBytes().toString(Charsets.UTF_8) }

    @Test
    fun `an export of the native app is read whole`() {
        val parsed = RcXml.parse(fixture())
        val data = parsed.data
        assertEquals("Проба с длинным именем", data.sampleName)
        assertEquals(10, data.spectrum.counts.size)
        assertEquals(864071L, data.spectrum.measurementSeconds)
        // Второй блок — это данные, а не оформление: он импортируется отдельной
        // строкой журнала, поэтому обязан дойти до вызывающего.
        val background = assertNotNull(data.background)
        assertEquals(10, background.counts.size)
        assertEquals(794449L, background.measurementSeconds)
    }

    @Test
    fun `a coefficient in scientific notation keeps its value`() {
        // 4.887E-4 в квадратичном члене: потеря экспоненты сдвинула бы шкалу
        // на сотни кэВ у верхнего края, и это было бы не видно на глаз.
        val spectrum = RcXml.parse(fixture()).data.spectrum
        assertEquals(-1.3376397f, spectrum.a0, 1e-6f)
        assertEquals(2.81f, spectrum.a1, 1e-6f)
        assertEquals(4.887e-4f, spectrum.a2, 1e-9f)
    }

    @Test
    fun `a fractional live time does not break the accumulation seconds`() {
        // MeasurementTime целое, LiveTime дробное — разбор не должен спотыкаться
        // ни о то, ни о другое.
        val parsed = RcXml.parse(fixture())
        assertTrue(parsed.warnings.none { it.contains("время измерения") }, "${parsed.warnings}")
    }

    @Test
    fun `the parser is configured with settings the device may refuse`() {
        // Полевой дефект: на телефоне падал ЛЮБОЙ импорт —
        // `UnsupportedOperationException: This parser does not support
        // specification "Unknown" version "0.0"`. Базовый класс JAXP реализует
        // `setXIncludeAware` броском исключения, Android его не
        // переопределяет, а Xerces на настольной JVM — переопределяет. Поэтому
        // ЭТОТ тест на JVM дефект поймать не мог и не может: он лишь держит
        // границу — разбор обязан работать, когда необязательные настройки
        // недоступны. Настоящая проверка — что каждая из них вызывается
        // отдельно и внутри runCatching (см. KDoc `parseDocument`).
        val source = java.io.File("src/main/kotlin/app/alpha/data/export/RcXml.kt")
            .readLines()
        val configured = Regex("""factory\.(isXIncludeAware|isExpandEntityReferences|setFeature)""")
        source.forEachIndexed { index, line ->
            val code = line.trimStart()
            // Упоминание в KDoc — это объяснение дефекта, а не вызов.
            if (code.startsWith("*") || code.startsWith("//")) return@forEachIndexed
            if (!configured.containsMatchIn(line)) return@forEachIndexed
            val previous = source.take(index).lastOrNull { it.isNotBlank() }?.trimEnd().orEmpty()
            val guarded = line.contains("runCatching") || previous.endsWith("runCatching {")
            assertTrue(guarded, "настройка разборщика без runCatching: $code")
        }
    }

    @Test
    fun `a foreign file never throws anything but the parser's own error`() {
        // Чужой файл — это ввод: на любом мусоре разбор обязан отвечать своей
        // ошибкой с причиной, а не произвольным исключением, из которого экран
        // импорта делает падение приложения.
        val broken = listOf(
            "",
            "не xml вовсе",
            "<ResultDataFile></ResultDataFile>",
            fixture().replace("<DataPoint>443</DataPoint>", "<DataPoint>—</DataPoint>"),
            fixture().replace("<MeasurementTime>864071</MeasurementTime>", ""),
            fixture().substring(0, fixture().length / 2),
        )
        for (text in broken) {
            try {
                RcXml.parse(text)
            } catch (_: RcXmlException) {
                // Ожидаемо: у отказа есть причина.
            } catch (e: Throwable) {
                throw AssertionError("посторонний тип ошибки: ${e::class.qualifiedName}", e)
            }
        }
    }
}
