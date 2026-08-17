package app.alpha.device

import kotlin.test.Test
import kotlin.test.assertEquals

class DoseUnitsTest {

    /** cdump #56 field evidence: raw 0.0005 displays as 5 uSv/h on the device. */
    @Test
    fun `raw value converts as rem per hour`() {
        assertEquals(5.0f, DoseUnits.rawToMicroSievertPerHour(0.0005f), 1e-4f)
        assertEquals(0.30f, DoseUnits.rawToMicroSievertPerHour(0.00003f), 1e-5f)
    }
}
