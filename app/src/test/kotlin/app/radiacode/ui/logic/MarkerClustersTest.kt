package app.radiacode.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * На длинном окне соседние всплески попадают в соседние пиксели, и над полем
 * вырастает стена почти наложенных треугольников: она перестаёт указывать на
 * что-либо конкретное и читается как шум.
 */
class MarkerClustersTest {

    @Test
    fun `markers further apart than the spacing stay separate`() {
        val clusters = MarkerClusters.of(
            listOf(10f to false, 60f to false, 110f to false),
            minSpacingPx = 12f,
        )

        assertEquals(3, clusters.size)
        assertTrue(clusters.all { it.count == 1 })
    }

    @Test
    fun `a burst becomes one marker carrying its count`() {
        val clusters = MarkerClusters.of(
            listOf(100f to false, 103f to false, 107f to false),
            minSpacingPx = 12f,
        )

        val only = clusters.single()
        assertEquals(3, only.count)
        // Позиция — середина группы: маркер указывает на то место, где были
        // события, а не на первое из них.
        assertEquals(103.33f, only.x, 0.01f)
    }

    @Test
    fun `the strongest class wins the group`() {
        // Иначе тревожный маркер ИСЧЕЗ бы, слившись с соседями другого класса,
        // — а он единственный, ради которого группу и разглядывают.
        val clusters = MarkerClusters.of(
            listOf(100f to false, 104f to true, 108f to false),
            minSpacingPx = 12f,
        )

        assertTrue(clusters.single().alarmClass)
    }

    @Test
    fun `a long chain does not collapse into a single marker`() {
        // Цепочка, где каждый следующий ближе порога к ПРЕДЫДУЩЕМУ, но далеко
        // от первого, — это не одна группа: иначе весь график схлопнулся бы в
        // один треугольник.
        val chain = (0 until 10).map { (it * 10).toFloat() to false }

        val clusters = MarkerClusters.of(chain, minSpacingPx = 12f)

        assertTrue(clusters.size > 1, "${clusters.size}")
        assertEquals(10, clusters.sumOf { it.count })
    }

    @Test
    fun `no markers, no clusters`() {
        assertTrue(MarkerClusters.of(emptyList(), minSpacingPx = 12f).isEmpty())
    }
}
