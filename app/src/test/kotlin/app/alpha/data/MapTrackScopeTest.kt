package app.alpha.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Persistence and default of the Карта scope («эта запись» / «все записи»). */
class MapTrackScopeTest {

    @Test
    fun `a stored choice survives a restart in either direction`() {
        assertEquals(MapTrackScope.ALL, MapTrackScope.fromStorage("ALL"))
        assertEquals(MapTrackScope.CURRENT, MapTrackScope.fromStorage("current"))
    }

    @Test
    fun `an unknown or missing value is not a choice`() {
        assertNull(MapTrackScope.fromStorage(null))
        assertNull(MapTrackScope.fromStorage(""))
        assertNull(MapTrackScope.fromStorage("heatmap"))
    }

    @Test
    fun `the stored choice always wins over the default`() {
        assertEquals(
            MapTrackScope.CURRENT,
            MapTrackScope.resolve(MapTrackScope.CURRENT, hasRecordings = true),
        )
        assertEquals(
            MapTrackScope.ALL,
            MapTrackScope.resolve(MapTrackScope.ALL, hasRecordings = false),
        )
    }

    @Test
    fun `without a choice the accumulated map is default once anything was recorded`() {
        assertEquals(MapTrackScope.ALL, MapTrackScope.resolve(null, hasRecordings = true))
    }

    @Test
    fun `an empty install lands on the state that teaches recording`() {
        assertEquals(MapTrackScope.CURRENT, MapTrackScope.resolve(null, hasRecordings = false))
    }
}
