package app.alpha.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Приложение обязано работать со ВСЕЙ серией: протокол общий, отличаются
 * характеристики детектора. Неопознанный прибор — рабочий случай, а не ошибка.
 */
class DeviceModelTest {

    @Test
    fun `the serial number carries the model`() {
        assertEquals(DeviceModel.RC_110, DeviceModel.fromSerial("RC-110-000123"))
        assertEquals(DeviceModel.RC_103, DeviceModel.fromSerial("RC-103-000001"))
        assertEquals(DeviceModel.RC_103G, DeviceModel.fromSerial("RC-103G-000001"))
        assertEquals(DeviceModel.RC_102, DeviceModel.fromSerial("RC-102-000115"))
        assertEquals(DeviceModel.ZERO, DeviceModel.fromSerial("RC-Zero-000007"))
        // Регистр и пробелы приходят от прибора как есть.
        assertEquals(DeviceModel.RC_103G, DeviceModel.fromSerial(" rc-103g-42 "))
    }

    @Test
    fun `an unknown device still works, on conservative parameters`() {
        for (serial in listOf(null, "", "RC-999-1", "SN12345", "RadiaCode")) {
            val model = DeviceModel.fromSerial(serial)
            assertEquals(DeviceModel.UNKNOWN, model, serial ?: "null")
            // Работает как спектрометр и получает самое широкое из известных
            // разрешений: узкое окно искало бы структуру там, где её нет.
            assertTrue(model.isSpectrometer)
            assertEquals(DeviceModel.DEFAULT_RESOLUTION_662, model.peakResolution662)
        }
    }

    @Test
    fun `published resolutions differ between crystals and are used as such`() {
        // GAGG у 103G даёт более узкие линии, чем CsI(Tl) у 103 и 110.
        assertTrue(DeviceModel.RC_103G.peakResolution662 < DeviceModel.RC_103.peakResolution662)
        assertEquals(DeviceModel.RC_110.peakResolution662, DeviceModel.RC_103.peakResolution662)
        // Модели без опубликованного числа его не выдумывают.
        assertEquals(null, DeviceModel.RC_102.resolution662)
        assertEquals(null, DeviceModel.UNKNOWN.resolution662)
    }

    @Test
    fun `a plastic scintillator is not a spectrometer`() {
        assertTrue(!DeviceModel.ZERO.isSpectrometer)
        assertEquals(null, DeviceModel.ZERO.resolution662)
        // Нижняя граница шкалы у Zero выше — это его собственный порог.
        assertTrue(DeviceModel.ZERO.minEnergyKeV > DeviceModel.RC_110.minEnergyKeV)
    }

    @Test
    fun `the info object derives the model from what the device reported`() {
        val info = DeviceInfo(
            address = "AA:BB",
            serialNumber = "RC-103G-000042",
            firmware = FwVersion(4, 9, "", 4, 9, ""),
            spectrumFormatVersion = 1,
        )
        assertEquals(DeviceModel.RC_103G, info.model)
        assertEquals("RadiaCode-103G", info.model.displayName)
    }
}
