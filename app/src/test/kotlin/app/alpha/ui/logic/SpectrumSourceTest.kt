package app.alpha.ui.logic

import app.alpha.device.DeviceModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Экран Спектра один на живой поток и на снимок из Истории, поэтому вопрос
 * «чей это спектр и чем его разбирать» решается правилом, а не порядком
 * элвисов в композиции.
 */
class SpectrumSourceTest {

    @Test
    fun `an open snapshot never mixes with the live stream`() {
        val source = SpectrumSources.choose(
            viewingSnapshot = true,
            hasSnapshot = true,
            hasMerged = true,
            hasLive = true,
            hasContinuation = true,
        )
        assertEquals(SpectrumSource.SNAPSHOT, source)
    }

    @Test
    fun `the live screen keeps its own priorities`() {
        assertEquals(
            SpectrumSource.MERGED_CONTINUATION,
            SpectrumSources.choose(false, hasSnapshot = false, hasMerged = true, hasLive = true, hasContinuation = true),
        )
        assertEquals(
            SpectrumSource.LIVE,
            SpectrumSources.choose(false, hasSnapshot = false, hasMerged = false, hasLive = true, hasContinuation = true),
        )
        assertEquals(
            SpectrumSource.CONTINUATION_ONLY,
            SpectrumSources.choose(false, hasSnapshot = false, hasMerged = false, hasLive = false, hasContinuation = true),
        )
        assertEquals(
            SpectrumSource.NONE,
            SpectrumSources.choose(false, hasSnapshot = false, hasMerged = false, hasLive = false, hasContinuation = false),
        )
        // Снимок не найден — показывать нечего, а не «покажем живой вместо него».
        assertEquals(
            SpectrumSource.NONE,
            SpectrumSources.choose(true, hasSnapshot = false, hasMerged = false, hasLive = true, hasContinuation = false),
        )
    }

    @Test
    fun `device actions are blocked by the snapshot even with an instrument connected`() {
        assertEquals(
            DeviceActionBlock.VIEWING_SNAPSHOT,
            SpectrumSources.deviceActionBlock(viewingSnapshot = true, connected = true),
        )
        assertEquals(
            DeviceActionBlock.NOT_CONNECTED,
            SpectrumSources.deviceActionBlock(viewingSnapshot = false, connected = false),
        )
        assertEquals(
            DeviceActionBlock.NONE,
            SpectrumSources.deviceActionBlock(viewingSnapshot = false, connected = true),
        )
    }

    @Test
    fun `a snapshot is analysed as an unidentified instrument`() {
        // Даже если рядом подключён опознанный прибор: он снимок не снимал.
        val model = SpectrumSources.analysisModel(DeviceModel.RC_103G, viewingSnapshot = true)
        assertEquals(DeviceModel.UNKNOWN, model)
        assertEquals(DeviceModel.DEFAULT_RESOLUTION_662, model.peakResolution662)
        assertFalse(SpectrumSources.modelIdentified(DeviceModel.RC_103G, viewingSnapshot = true))

        assertEquals(
            DeviceModel.RC_103G,
            SpectrumSources.analysisModel(DeviceModel.RC_103G, viewingSnapshot = false),
        )
        assertTrue(SpectrumSources.modelIdentified(DeviceModel.RC_103G, viewingSnapshot = false))
        assertFalse(SpectrumSources.modelIdentified(null, viewingSnapshot = false))
        // Самое широкое разрешение — не «поточнее на всякий случай»: узкое
        // окно искало бы структуру там, где её нет.
        assertTrue(DeviceModel.UNKNOWN.peakResolution662 >= DeviceModel.RC_103G.peakResolution662)
    }
}
