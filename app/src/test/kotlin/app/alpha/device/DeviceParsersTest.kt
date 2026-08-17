package app.alpha.device

import app.alpha.protocol.BytesReader
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceParsersTest {

    @Test
    fun `parses GET_VERSION body`() {
        val body = Wire.u16(9) + Wire.u16(4) + Wire.str("Feb 01 2024") +
            Wire.u16(15) + Wire.u16(4) + Wire.str("Mar 02 2025")
        val version = DeviceParsers.parseVersion(BytesReader(body))
        assertEquals(4, version.bootMajor)
        assertEquals(9, version.bootMinor)
        assertEquals("Feb 01 2024", version.bootDate)
        assertEquals(4, version.targetMajor)
        assertEquals(15, version.targetMinor)
        assertEquals("Mar 02 2025", version.targetDate)
    }

    @Test
    fun `firmware version comparison`() {
        val v48 = FwVersion(4, 9, "", 4, 8, "")
        assertTrue(v48.targetAtLeast(4, 8))
        assertTrue(FwVersion(4, 9, "", 5, 0, "").targetAtLeast(4, 8))
        assertTrue(FwVersion(4, 9, "", 4, 15, "").targetAtLeast(4, 8))
        assertFalse(FwVersion(4, 9, "", 4, 7, "").targetAtLeast(4, 8))
        assertFalse(FwVersion(4, 9, "", 3, 20, "").targetAtLeast(4, 8))
    }

    @Test
    fun `parses SpecFormatVersion from cp1251 configuration`() {
        val text = "DeviceName=РадиаКод-110\r\nSpecFormatVersion=1\r\nOther=2\r\n"
        val payload = text.toByteArray(Charset.forName("windows-1251"))
        val config = DeviceParsers.parseConfiguration(payload)
        assertEquals(1, config.specFormatVersion)
        assertTrue("РадиаКод-110" in config.text)
    }

    @Test
    fun `missing SpecFormatVersion defaults to 0`() {
        val config = DeviceParsers.parseConfiguration("A=B\r\n".toByteArray(Charsets.US_ASCII))
        assertEquals(0, config.specFormatVersion)
    }
}
