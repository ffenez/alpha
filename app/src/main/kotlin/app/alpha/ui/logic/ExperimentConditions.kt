package app.alpha.ui.logic

import app.alpha.data.db.ExperimentEntity
import app.alpha.ui.text.ExperimentRu
import app.alpha.ui.text.ExperimentStrings

/**
 * Условия опыта, разложенные на то, что человек может повторить буквально.
 *
 * ## Зачем не одно поле
 *
 * Раньше это была одна строка «геометрия», написанная от руки один раз.
 * Через неделю по фразе «тарелка на столе, 5 см» повторить постановку можно
 * только приблизительно, а весь смысл A/B в том, что между прогонами меняется
 * РОВНО ОДНО. Разложенные условия приложение показывает перед каждым
 * следующим прогоном — не как совет, а как список того, что уже было.
 *
 * Приложение по-прежнему ничего не проверяет: расстояние и ориентацию оно
 * знать не может. Оно только помнит сказанное и напоминает вовремя.
 */
data class ExperimentConditions(
    /** Расстояние до объекта, см; null — не задано. */
    val distanceCm: Int? = null,
    /** Код положения прибора; «» — не задано. */
    val placement: String = "",
    /** Код ориентации прибора; «» — не задано. */
    val orientation: String = "",
    /** Плановая длительность прогона, с; 0 — не задана. */
    val plannedSeconds: Long = 0,
) {
    val isEmpty: Boolean
        get() = distanceCm == null &&
            placement.isBlank() &&
            orientation.isBlank() &&
            plannedSeconds <= 0L

    companion object {
        /**
         * Положения прибора — КОДАМИ, а не подписями.
         *
         * В базу уходит код: подпись зависит от языка интерфейса, и опыт,
         * созданный по-русски, обязан читаться по-английски тем же опытом, а
         * не строкой на чужом языке.
         */
        val PLACEMENTS = listOf("table", "hand", "tripod", "floor")

        val ORIENTATIONS = listOf("screen_up", "screen_to_object", "back_to_object", "edge")

        fun of(experiment: ExperimentEntity): ExperimentConditions = ExperimentConditions(
            distanceCm = experiment.distanceCm,
            placement = experiment.placement,
            orientation = experiment.orientation,
            plannedSeconds = experiment.plannedSeconds,
        )
    }
}

/** Сценарий на экране: три ответа на вопрос «что с чем сравниваем». */
enum class ExperimentScenario(val kind: String) {
    /** A — фон, B — объект. */
    OBJECT(ExperimentEntity.KIND_BACKGROUND_VS_OBJECT),

    /** A — место 1, B — место 2. */
    PLACES(ExperimentEntity.KIND_PLACE_VS_PLACE),

    /** A и B называет сам человек; шаблоны — расстояние, экранирование, другое. */
    CUSTOM(ExperimentEntity.KIND_CUSTOM),
    ;

    companion object {
        /**
         * Шаблоны «своих условий»: те же виды опыта, что были в общем списке.
         *
         * Список из четырёх пунктов смешивал РАЗНОЕ: «фон и объект» и «место и
         * место» отвечают на вопрос «что с чем», а «расстояние» и
         * «экранирование» — на вопрос «что меняется между прогонами».
         * Вопросов два, поэтому и выборов теперь два.
         */
        val TEMPLATES = listOf(
            ExperimentEntity.KIND_DISTANCE,
            ExperimentEntity.KIND_SHIELDING,
            ExperimentEntity.KIND_CUSTOM,
        )

        fun of(kind: String): ExperimentScenario = when (kind) {
            ExperimentEntity.KIND_BACKGROUND_VS_OBJECT -> OBJECT
            ExperimentEntity.KIND_PLACE_VS_PLACE -> PLACES
            else -> CUSTOM
        }
    }
}

/** Как условия называются человеку. Логика чистая, экран только рисует. */
object ExperimentConditionsFormat {

    fun placementLabel(code: String, s: ExperimentStrings = ExperimentRu): String = when (code) {
        "table" -> s.placementTable
        "hand" -> s.placementHand
        "tripod" -> s.placementTripod
        "floor" -> s.placementFloor
        else -> ""
    }

    fun orientationLabel(code: String, s: ExperimentStrings = ExperimentRu): String = when (code) {
        "screen_up" -> s.orientationScreenUp
        "screen_to_object" -> s.orientationScreenToObject
        "back_to_object" -> s.orientationBackToObject
        "edge" -> s.orientationEdge
        else -> ""
    }

    fun scenarioLabel(
        scenario: ExperimentScenario,
        s: ExperimentStrings = ExperimentRu,
    ): String = when (scenario) {
        ExperimentScenario.OBJECT -> s.scenarioObject
        ExperimentScenario.PLACES -> s.scenarioPlaces
        ExperimentScenario.CUSTOM -> s.scenarioCustom
    }

    fun scenarioHint(
        scenario: ExperimentScenario,
        s: ExperimentStrings = ExperimentRu,
    ): String = when (scenario) {
        ExperimentScenario.OBJECT -> s.scenarioObjectHint
        ExperimentScenario.PLACES -> s.scenarioPlacesHint
        ExperimentScenario.CUSTOM -> s.scenarioCustomHint
    }

    fun templateLabel(kind: String, s: ExperimentStrings = ExperimentRu): String = when (kind) {
        ExperimentEntity.KIND_DISTANCE -> s.templateDistance
        ExperimentEntity.KIND_SHIELDING -> s.templateShielding
        else -> s.templateOther
    }

    /**
     * «5 см · прибор на столе · экраном вверх · 10 мин» — одной строкой.
     *
     * Незаданное просто отсутствует: прочерк на месте расстояния сообщал бы,
     * что расстояние есть и оно неизвестно, а его чаще всего просто нет
     * (сравнение двух мест).
     */
    fun summary(
        conditions: ExperimentConditions,
        s: ExperimentStrings = ExperimentRu,
    ): String {
        val parts = listOfNotNull(
            conditions.distanceCm?.let { s.centimeters(it) },
            placementLabel(conditions.placement, s).takeIf { it.isNotBlank() },
            orientationLabel(conditions.orientation, s).takeIf { it.isNotBlank() },
            duration(conditions.plannedSeconds, s),
        )
        return parts.joinToString(" · ")
    }

    /** «Повторите условия A · 5 см · прибор на столе · 10 мин». */
    fun repeatLine(
        letter: String,
        conditions: ExperimentConditions,
        s: ExperimentStrings = ExperimentRu,
    ): String? {
        val summary = summary(conditions, s)
        if (summary.isBlank()) return null
        return s.repeatConditions(letter, summary)
    }

    private fun duration(seconds: Long, s: ExperimentStrings): String? = when {
        seconds <= 0L -> null
        seconds % 60L == 0L -> s.minutes(seconds / 60L)
        else -> s.seconds(seconds)
    }
}
