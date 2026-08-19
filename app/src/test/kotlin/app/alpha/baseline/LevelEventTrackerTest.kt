package app.alpha.baseline

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Приёмка из `history_semantic_events_redesign.md`: спокойный фон не рождает
 * событий, один эпизод остаётся одним, возврат закрывает его, порог отделён от
 * изменения уровня.
 */
class LevelEventTrackerTest {

    private val thresholds = AlarmThresholds(
        l1MicroSvH = 0.30f,
        l2MicroSvH = 1.00f,
        relativeFactor = 2.0f,
        persistenceSeconds = 120,
    )

    private val persistence = 120_000L
    private val baselineHigh = 0.18f

    private fun tracker() = LevelEventTracker(persistenceMillis = persistence)

    /** Прогон ряда значений с шагом в секунду; возвращает все переходы. */
    private fun run(
        tracker: LevelEventTracker,
        values: List<Float>,
        startMillis: Long = 0L,
        stepMillis: Long = 1_000L,
        baseline: Float? = baselineHigh,
    ): List<LevelEventTransition> = values.mapIndexed { i, value ->
        tracker.onSample(startMillis + i * stepMillis, value, baseline, thresholds)
    }

    @Test
    fun `спокойный фон за сутки не даёт ни одного события`() {
        // Профиль около 0,16 при обычном верхе 0,18: значения 0,14–0,17 —
        // обычные колебания, а не событие. Сутки при отсчёте раз в секунду.
        val values = List(86_400) { i ->
            0.155f + when (i % 4) {
                0 -> -0.015f
                1 -> -0.005f
                2 -> 0.005f
                else -> 0.015f
            }
        }
        val transitions = run(tracker(), values)
        assertTrue(
            transitions.all { it is LevelEventTransition.None },
            "событий: ${transitions.count { it !is LevelEventTransition.None }}",
        )
    }

    @Test
    fun `одно длительное изменение остаётся одним событием`() {
        val tracker = tracker()
        // Двадцать минут на 0,42 — выше 2×P90 (0,36) и выше порога 0,30.
        val transitions = run(tracker, List(1_200) { 0.42f })
        val opened = transitions.filterIsInstance<LevelEventTransition.Opened>()
        val closed = transitions.filterIsInstance<LevelEventTransition.Closed>()
        assertEquals(1, opened.size, "открытий: ${opened.size}")
        assertEquals(0, closed.size, "эпизод закрылся, не закончившись")
        val last = transitions.filterIsInstance<LevelEventTransition.Updated>().last().event
        assertEquals(0L, last.startMillis, "начало эпизода не в первом отсчёте")
        assertTrue(last.durationMillis >= 1_190_000L, "длительность ${last.durationMillis}")
        assertTrue(last.active)
    }

    @Test
    fun `пределы и среднее накапливаются с начала эпизода, а не с подтверждения`() {
        val tracker = tracker()
        val values = List(200) { 0.40f } + List(200) { 0.60f }
        val transitions = run(tracker, values)
        val event = transitions.filterIsInstance<LevelEventTransition.Updated>().last().event
        assertEquals(0.40f, event.minMicroSvH, 1e-4f)
        assertEquals(0.60f, event.maxMicroSvH, 1e-4f)
        assertEquals(400, event.sampleCount, "отсчётов ${event.sampleCount}")
        assertEquals(0.50f, event.meanMicroSvH, 1e-3f)
    }

    @Test
    fun `возврат к обычному закрывает эпизод один раз`() {
        val tracker = tracker()
        val values = List(600) { 0.42f } + List(600) { 0.16f }
        val transitions = run(tracker, values)
        assertEquals(1, transitions.filterIsInstance<LevelEventTransition.Opened>().size)
        val closed = transitions.filterIsInstance<LevelEventTransition.Closed>()
        assertEquals(1, closed.size, "закрытий: ${closed.size}")
        val event = closed.single().event
        assertTrue(!event.active)
        // Конец — момент возврата (600-я секунда), а не момент подтверждения.
        assertEquals(600_000L, event.endMillis)
        assertEquals(600_000L, event.durationMillis)
    }

    @Test
    fun `после закрытия новый эпизод открывается заново`() {
        val tracker = tracker()
        val values = List(400) { 0.42f } + List(400) { 0.16f } + List(400) { 0.42f }
        val transitions = run(tracker, values)
        assertEquals(2, transitions.filterIsInstance<LevelEventTransition.Opened>().size)
        assertEquals(1, transitions.filterIsInstance<LevelEventTransition.Closed>().size)
    }

    @Test
    fun `колебание у порога не рвёт эпизод на десятки`() {
        val tracker = tracker()
        // Значение гуляет вокруг порога 0,30, но не возвращается внутрь
        // обычного (0,18) — это ОДИН эпизод, а не череда.
        val values = List(3_600) { i -> if (i % 2 == 0) 0.32f else 0.28f }
        val transitions = run(tracker, values)
        assertEquals(
            1,
            transitions.filterIsInstance<LevelEventTransition.Opened>().size,
            "эпизод разорвался",
        )
        assertEquals(0, transitions.filterIsInstance<LevelEventTransition.Closed>().size)
    }

    @Test
    fun `превышение порога и изменение уровня — разные виды`() {
        // 0,40 достигает порога 0,30.
        val overThreshold = run(tracker(), List(300) { 0.40f })
            .filterIsInstance<LevelEventTransition.Opened>().single().event
        assertEquals(LevelEventKind.THRESHOLD, overThreshold.kind)

        // 0,25 — вдвое выше обычного 0,12, но порога 0,30 не достигает.
        val levelChange = run(tracker(), List(300) { 0.25f }, baseline = 0.12f)
            .filterIsInstance<LevelEventTransition.Opened>().single().event
        assertEquals(LevelEventKind.LEVEL_CHANGE, levelChange.kind)
        assertEquals(0.30f, levelChange.thresholdMicroSvH, 1e-6f)
    }

    @Test
    fun `дошедшее до порога изменение уровня становится превышением`() {
        val tracker = tracker()
        val values = List(300) { 0.25f } + List(300) { 0.35f }
        val transitions = run(tracker, values, baseline = 0.12f)
        assertEquals(LevelEventKind.LEVEL_CHANGE, transitions
            .filterIsInstance<LevelEventTransition.Opened>().single().event.kind)
        val last = transitions.filterIsInstance<LevelEventTransition.Updated>().last().event
        assertEquals(LevelEventKind.THRESHOLD, last.kind, "эпизод не поднялся до порога")
        // Второго события об одном эпизоде не появилось.
        assertEquals(1, transitions.filterIsInstance<LevelEventTransition.Opened>().size)
    }

    @Test
    fun `короткий выброс не открывает события`() {
        val tracker = tracker()
        // Минута выше условия при выдержке в две минуты.
        val transitions = run(tracker, List(60) { 0.42f } + List(300) { 0.16f })
        assertTrue(transitions.none { it is LevelEventTransition.Opened })
    }

    @Test
    fun `отношение к обычному считается по среднему эпизода`() {
        val event = run(tracker(), List(300) { 0.36f })
            .filterIsInstance<LevelEventTransition.Opened>().single().event
        val ratio = event.ratioToBaseline
        assertTrue(ratio != null && abs(ratio - 2.0f) < 0.01f, "отношение $ratio")
    }

    @Test
    fun `без изученного фона отношения нет`() {
        val event = run(tracker(), List(300) { 0.42f }, baseline = null)
            .filterIsInstance<LevelEventTransition.Opened>().single().event
        assertNull(event.ratioToBaseline)
        assertNull(event.baselineHighMicroSvH)
        assertEquals(LevelEventKind.THRESHOLD, event.kind)
    }

    @Test
    fun `принудительное закрытие завершает идущий эпизод`() {
        val tracker = tracker()
        run(tracker, List(300) { 0.42f })
        val closed = tracker.closeNow(endMillis = 500_000L)
        assertTrue(closed is LevelEventTransition.Closed)
        assertEquals(500_000L, (closed as LevelEventTransition.Closed).event.endMillis)
        assertNull(tracker.active)
        // Второй раз закрывать нечего.
        assertTrue(tracker.closeNow(600_000L) is LevelEventTransition.None)
    }

    @Test
    fun `короткий провал внутрь обычного эпизод не закрывает`() {
        val tracker = tracker()
        // Десять секунд спокойствия при выдержке возврата в две минуты.
        val values = List(300) { 0.42f } + List(10) { 0.16f } + List(300) { 0.42f }
        val transitions = run(tracker, values)
        assertEquals(0, transitions.filterIsInstance<LevelEventTransition.Closed>().size)
        assertEquals(1, transitions.filterIsInstance<LevelEventTransition.Opened>().size)
    }
}
