package app.radiacode.smoke

import app.radiacode.data.export.RcXml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Регрессия полевого краша №1: импорт RC-XML падал НА УСТРОЙСТВЕ, потому что
 * `factory.isXIncludeAware = false` у андроидного разборщика кидает
 * `UnsupportedOperationException`, а настольный Xerces это молча умеет — и
 * JVM-тесты дефект не видели. Здесь та же фикстура штатной выгрузки
 * разбирается через [AndroidLikeDocumentBuilderFactory]: каждая необязательная
 * настройка разборщика обязана переживать отказ среды.
 */
class RcXmlAndroidParserRegressionTest {

    private fun fixture(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("native-app-export.xml"))
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private fun withAndroidLikeParser(block: () -> Unit) {
        val key = AndroidLikeDocumentBuilderFactory.FACTORY_PROPERTY
        val previous = System.getProperty(key)
        System.setProperty(key, AndroidLikeDocumentBuilderFactory::class.java.name)
        try {
            block()
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }

    @Test
    fun `the test fixture parses under an Android-like parser`() = withAndroidLikeParser {
        val data = RcXml.parse(fixture()).data
        assertEquals(10, data.spectrum.counts.size)
        // Второй спектр файла — данные, обязан дойти и через андроидный разбор.
        assertNotNull(data.background)
    }

    @Test
    fun `our own export round-trips under an Android-like parser`() = withAndroidLikeParser {
        val parsed = RcXml.parse(fixture()).data
        val rewritten = RcXml.write(parsed)
        val reparsed = RcXml.parse(rewritten).data
        assertEquals(parsed.spectrum.counts, reparsed.spectrum.counts)
    }
}
