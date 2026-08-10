package app.radiacode.data

import app.radiacode.analysis.AbAnalysis
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flat JSON storage of derived-result parameters (spec §22) and its codec. */
class JsonMapTest {

    private lateinit var previousLocale: Locale

    @BeforeTest
    fun setUp() {
        previousLocale = Locale.getDefault()
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun `encode and decode round-trip preserving order`() {
        val values = linkedMapOf("method" to "interval", "run" to "A", "n" to "42")
        val encoded = JsonMap.encode(values)
        assertEquals("""{"method":"interval","run":"A","n":"42"}""", encoded)
        assertEquals(values, JsonMap.decode(encoded))
    }

    @Test
    fun `quotes, backslashes and newlines survive`() {
        val values = mapOf("note" to "он сказал \"стоп\"\nи \\ушёл")
        val decoded = JsonMap.decode(JsonMap.encode(values))
        assertEquals(values, decoded)
    }

    @Test
    fun `numbers are locale-independent on disk`() {
        // A comma-decimal locale must not put «0,5» into the database.
        Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        val encoded = JsonMap.of("value" to 0.5, "count" to 7)
        assertTrue(encoded.contains("0.500000"), encoded)
        assertTrue(encoded.contains("\"7\""), encoded)
    }

    @Test
    fun `null values are dropped`() {
        assertEquals("""{"a":"1"}""", JsonMap.of("a" to 1, "b" to null))
    }

    @Test
    fun `malformed input decodes to an empty map instead of throwing`() {
        assertEquals(emptyMap(), JsonMap.decode(null))
        assertEquals(emptyMap(), JsonMap.decode(""))
        assertEquals(emptyMap(), JsonMap.decode("not json"))
        assertEquals(emptyMap(), JsonMap.decode("{\"a\":}"))
        assertEquals(emptyMap(), JsonMap.decode("{42:1}"))
        // A truncated pair keeps whatever parsed cleanly before it.
        assertEquals(mapOf("a" to "1"), JsonMap.decode("{\"a\":\"1\",\"b\"}"))
    }

    @Test
    fun `dose statistics round-trip through the run column`() {
        val stats = assertNotNull(AbAnalysis.doseStats(listOf(0.10, 0.12, 0.14)))
        val encoded = DoseStatsCodec.encode(stats)
        val decoded = assertNotNull(DoseStatsCodec.decode(encoded))
        assertEquals(stats.sampleCount, decoded.sampleCount)
        assertEquals(stats.meanMicroSvH, decoded.meanMicroSvH, 1e-5)
        assertEquals(stats.sdMicroSvH, decoded.sdMicroSvH, 1e-5)
        assertEquals(stats.minMicroSvH, decoded.minMicroSvH, 1e-5)
        assertEquals(stats.maxMicroSvH, decoded.maxMicroSvH, 1e-5)
    }

    @Test
    fun `a recording run stores its start snapshot and no statistics yet`() {
        val encoded = DoseStatsCodec.encode(
            stats = null,
            extra = mapOf(DoseStatsCodec.KEY_START_SPECTRUM to "17"),
        )
        assertNull(DoseStatsCodec.decode(encoded), "no readings yet means no statistics")
        assertEquals(17L, DoseStatsCodec.startSpectrumId(encoded))
        assertNull(DoseStatsCodec.startSpectrumId(""))
    }

    @Test
    fun `the start snapshot survives finishing the run`() {
        val stats = assertNotNull(AbAnalysis.doseStats(listOf(0.1, 0.2)))
        val encoded = DoseStatsCodec.encode(
            stats = stats,
            extra = mapOf(DoseStatsCodec.KEY_START_SPECTRUM to "5"),
        )
        assertEquals(5L, DoseStatsCodec.startSpectrumId(encoded))
        assertEquals(2, assertNotNull(DoseStatsCodec.decode(encoded)).sampleCount)
    }

    @Test
    fun `an empty statistics column decodes to null`() {
        assertEquals("", DoseStatsCodec.encode(null))
        assertNull(DoseStatsCodec.decode(""))
        assertNull(DoseStatsCodec.decode(null))
    }
}
