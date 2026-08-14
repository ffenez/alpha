package app.radiacode.ui.logic

import app.radiacode.data.db.ExperimentEntity
import app.radiacode.ui.text.ExperimentEn
import app.radiacode.ui.text.ExperimentRu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Условия опыта — то, что человек повторяет буквально.
 *
 * Проверяется главное свойство: в базу уходят КОДЫ, а на экран — подписи, и
 * опыт, поставленный по-русски, читается по-английски тем же опытом. Плюс
 * второе: незаданное условие отсутствует, а не печатается прочерком.
 */
class ExperimentConditionsTest {

    private val full = ExperimentConditions(
        distanceCm = 5,
        placement = "table",
        orientation = "screen_up",
        plannedSeconds = 600,
    )

    @Test
    fun `the conditions read as one human line`() {
        val summary = ExperimentConditionsFormat.summary(full, ExperimentRu)

        assertEquals("5 см · на столе · экраном вверх · 10 мин", summary)
    }

    @Test
    fun `what was not set is absent, not a dash`() {
        val partial = ExperimentConditions(placement = "hand", plannedSeconds = 120)
        val summary = ExperimentConditionsFormat.summary(partial, ExperimentRu)

        assertEquals("в руке · 2 мин", summary)
        assertTrue(!summary.contains("—"), summary)
        assertTrue(ExperimentConditions().isEmpty)
        assertTrue(!partial.isEmpty)
    }

    /** Язык опыта — язык читателя, а не автора: в базе лежит код. */
    @Test
    fun `the same experiment reads in either language`() {
        val ru = ExperimentConditionsFormat.summary(full, ExperimentRu)
        val en = ExperimentConditionsFormat.summary(full, ExperimentEn)

        assertEquals("5 cm · on a table · screen up · 10 min", en)
        assertTrue(ru != en)
    }

    @Test
    fun `the reminder names the run it repeats and says nothing when there is nothing to repeat`() {
        val line = assertNotNull(ExperimentConditionsFormat.repeatLine("A", full, ExperimentRu))

        assertTrue(line.contains("A"), line)
        assertTrue(line.contains("5 см"), line)
        assertTrue(line.contains("10 мин"), line)
        assertNull(ExperimentConditionsFormat.repeatLine("A", ExperimentConditions(), ExperimentRu))
    }

    /**
     * Сценарий отвечает на вопрос «что с чем», шаблон — «что меняется».
     * Прежний список из четырёх пунктов смешивал эти два вопроса в один.
     */
    @Test
    fun `three scenarios cover the four stored kinds`() {
        assertEquals(ExperimentScenario.OBJECT, ExperimentScenario.of(ExperimentEntity.KIND_BACKGROUND_VS_OBJECT))
        assertEquals(ExperimentScenario.PLACES, ExperimentScenario.of(ExperimentEntity.KIND_PLACE_VS_PLACE))
        assertEquals(ExperimentScenario.CUSTOM, ExperimentScenario.of(ExperimentEntity.KIND_DISTANCE))
        assertEquals(ExperimentScenario.CUSTOM, ExperimentScenario.of(ExperimentEntity.KIND_SHIELDING))
        assertEquals(ExperimentScenario.CUSTOM, ExperimentScenario.of(ExperimentEntity.KIND_CUSTOM))
        // Каждый шаблон «своих условий» остаётся сохраняемым видом опыта.
        for (kind in ExperimentScenario.TEMPLATES) {
            assertTrue(kind in ExperimentEntity.KINDS, kind)
        }
        for (scenario in ExperimentScenario.entries) {
            assertTrue(ExperimentConditionsFormat.scenarioLabel(scenario, ExperimentRu).isNotBlank())
            assertTrue(ExperimentConditionsFormat.scenarioHint(scenario, ExperimentRu).isNotBlank())
            assertTrue(ExperimentConditionsFormat.scenarioLabel(scenario, ExperimentEn).isNotBlank())
        }
    }

    @Test
    fun `every code offered by the screen has a name in both languages`() {
        for (code in ExperimentConditions.PLACEMENTS) {
            assertTrue(ExperimentConditionsFormat.placementLabel(code, ExperimentRu).isNotBlank(), code)
            assertTrue(ExperimentConditionsFormat.placementLabel(code, ExperimentEn).isNotBlank(), code)
        }
        for (code in ExperimentConditions.ORIENTATIONS) {
            assertTrue(ExperimentConditionsFormat.orientationLabel(code, ExperimentRu).isNotBlank(), code)
            assertTrue(ExperimentConditionsFormat.orientationLabel(code, ExperimentEn).isNotBlank(), code)
        }
        // Неизвестный код молчит, а не показывает себя человеку.
        assertEquals("", ExperimentConditionsFormat.placementLabel("ceiling", ExperimentRu))
    }

    @Test
    fun `an experiment row carries its conditions back`() {
        val entity = ExperimentEntity(
            kind = ExperimentEntity.KIND_CUSTOM,
            createdAt = 0L,
            algorithmVersion = 1,
            distanceCm = 5,
            placement = "tripod",
            orientation = "edge",
            plannedSeconds = 300,
        )

        val conditions = ExperimentConditions.of(entity)

        assertEquals(5, conditions.distanceCm)
        assertEquals("tripod", conditions.placement)
        assertEquals("edge", conditions.orientation)
        assertEquals(300L, conditions.plannedSeconds)
    }
}
