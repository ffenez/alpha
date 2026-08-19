package app.alpha.ui.logic

/**
 * Состояние экрана «Поиск» — ОДНО и непротиворечивое.
 *
 * ## Зачем состояние, а не набор флагов
 *
 * Пока экран сам решал, показывать ли «ждём данные», рисовать ли стрелку и
 * можно ли нажать кнопку, эти решения принимались из разных источников и
 * расходились: живое число уже шло, график рисовался, точка отсчёта стояла — а
 * экран писал, что ждёт прибор. Взаимоисключающие состояния возникали не из-за
 * ошибки в условии, а из-за того, что условий было несколько.
 *
 * Здесь состояние выводится один раз из трёх фактов: есть ли свежий отсчёт,
 * есть ли точка отсчёта и посчитано ли сравнение. Всё остальное — производные
 * этого состояния.
 *
 * ## Что здесь НЕ решается
 *
 * Достоверность вывода. Отношение и вердикт — разные вещи: отношение
 * вычислимо, как только есть оба числа, а вердикт требует статистики. Поэтому
 * недостаток статистики никогда не прячет ни стрелку, ни отношение — он
 * меняет только [ReferenceReady.confidence].
 */
sealed interface SearchUiState {

    /** Прибор не подключён: сравнивать нечего и нечем. */
    data object NoDevice : SearchUiState

    /** Прибор на связи, но свежего отсчёта нет — единственный случай «ждём». */
    data object WaitingForLiveData : SearchUiState

    /**
     * Данные идут, точки отсчёта нет.
     *
     * Стрелка всё равно стоит, если приложению есть с чем сравнивать само:
     * [localRatio] — отношение короткого окна к предыдущему, тому самому
     * «недавнему уровню», по которому уже считается направление. Пустой прибор
     * до нажатия кнопки означал, что человек видит стрелку только после
     * действия, хотя сравнение шло с первой секунды. Знаменатель при этом
     * ДРУГОЙ, и экран обязан его называть.
     */
    data class LiveNoReference(
        val cps: Float,
        val localRatio: Double? = null,
    ) : SearchUiState

    /**
     * Данные идут и точка отсчёта стоит.
     *
     * [ratio] считается всегда, когда есть оба числа, поэтому стрелка стоит
     * всегда. [confidence] отвечает на другой вопрос — можно ли на разницу
     * опереться, — и на положение стрелки не влияет.
     */
    data class ReferenceReady(
        val cps: Float,
        val referenceCps: Double,
        val ratio: Double,
        val confidence: SearchConfidence,
    ) : SearchUiState

    /** Идёт ли поток прямо сейчас — общий признак для рисования. */
    val live: Boolean get() = this !is NoDevice && this !is WaitingForLiveData

    /**
     * Отношение, которое показывает стрелка; null — сравнивать не с чем.
     *
     * У поставленной точки отсчёта это отношение к ней, без неё — к недавнему
     * уровню. Что именно в знаменателе, говорит [againstMark].
     */
    val ratioOrNull: Double?
        get() = when (this) {
            is ReferenceReady -> ratio
            is LiveNoReference -> localRatio
            else -> null
        }

    /** В знаменателе поставленная рукой точка, а не собственный расчёт приложения. */
    val againstMark: Boolean get() = this is ReferenceReady

    /** Стоит ли стрелка. Ровно одно условие, и оно не про статистику. */
    val needleVisible: Boolean get() = ratioOrNull != null

    /** Показывать ли большое действие «запомнить уровень». */
    val offersReference: Boolean get() = this is LiveNoReference
}

/** Насколько выводу о разнице можно верить — отдельно от самой разницы. */
enum class SearchConfidence {
    /** Сравнение ещё не посчитано или его окна слишком коротки. */
    INSUFFICIENT,

    /** Критерий проверил различие и не нашёл его. */
    NO_DIFFERENCE,

    /** Различие подтверждено: счёт выше точки отсчёта. */
    ABOVE,

    /** Различие подтверждено: счёт ниже точки отсчёта. */
    BELOW,
}

object SearchUiStates {

    /**
     * Сколько отсчёт считается свежим, мс.
     *
     * **Инженерный параметр**: прибор пишет раз в секунду, три пропущенные
     * секунды — уже не задержка доставки, а обрыв. Меньший порог мигал бы на
     * обычных задержках BLE, больший — держал бы на экране мёртвое число.
     */
    const val LIVE_TIMEOUT_MILLIS = 3_000L

    /**
     * Состояние экрана по фактам.
     *
     * @param cps последнее показание счёта; null — показания не было
     * @param receivedAtMillis когда оно ПРИШЛО (часы телефона), не когда измерено
     * @param nowMillis сейчас по тем же часам
     * @param connected есть ли соединение с прибором
     * @param navigate состояние наведения: точка отсчёта и её сравнение
     */
    fun of(
        cps: Float?,
        receivedAtMillis: Long?,
        nowMillis: Long,
        connected: Boolean,
        navigate: NavigateState,
    ): SearchUiState {
        if (!connected && cps == null) return SearchUiState.NoDevice
        val fresh = cps != null &&
            receivedAtMillis != null &&
            nowMillis - receivedAtMillis <= LIVE_TIMEOUT_MILLIS
        if (!fresh) {
            return if (connected) SearchUiState.WaitingForLiveData else SearchUiState.NoDevice
        }
        val reference = navigate.reference?.ratePerSecond?.takeIf { it > 0.0 && it.isFinite() }
            ?: return SearchUiState.LiveNoReference(
                cps = cps!!,
                // Пока точки нет, знаменатель считает сам движок: короткое
                // окно против предыдущего. Это то же сравнение, по которому
                // уже показано направление, поэтому второй арифметики здесь
                // не заводится.
                localRatio = navigate.trendComparison?.ratio?.takeIf {
                    it.isFinite() && it > 0.0
                },
            )
        // Числитель — лучшая оценка текущей скорости: короткое окно, если оно
        // набрано, иначе сам отсчёт. Ждать окна нельзя — стрелка обязана стоять
        // с первой секунды после того, как точка отсчёта поставлена.
        val current = navigate.fast?.ratePerSecond?.takeIf { it.isFinite() } ?: cps!!.toDouble()
        return SearchUiState.ReferenceReady(
            cps = cps,
            referenceCps = reference,
            ratio = current / reference,
            confidence = confidenceOf(navigate),
        )
    }

    /** Вердикт о РАЗНИЦЕ — отдельно от её величины. */
    fun confidenceOf(navigate: NavigateState): SearchConfidence {
        val comparison = navigate.referenceComparison ?: return SearchConfidence.INSUFFICIENT
        if (!comparison.ratioLow.isFinite() || !comparison.ratioHigh.isFinite()) {
            return SearchConfidence.INSUFFICIENT
        }
        // Интервал накрыл единицу — различие не принято. Это не равенство:
        // критерий проверил отличие и не нашёл его, так это и называется.
        if (comparison.ratioLow <= 1.0 && comparison.ratioHigh >= 1.0) {
            return SearchConfidence.NO_DIFFERENCE
        }
        return if (comparison.ratio > 1.0) SearchConfidence.ABOVE else SearchConfidence.BELOW
    }
}
