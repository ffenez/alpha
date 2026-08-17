package app.alpha.ui.logic

import app.alpha.analysis.CountWindow
import app.alpha.baseline.Baseline

/**
 * Фон, изученный самим приложением, — для повседневного поиска.
 *
 * ## Что это
 *
 * Обычный фон МЕСТА собирается непрерывно: медиана, P10–P90, MAD, с правилами
 * допуска (не учить во время эксперимента, не учить после отклонения, не
 * учить чужой прибор). Поиск пользуется этой моделью, не требуя отдельной
 * записи фона.
 *
 * ## Чем он не является
 *
 * Он не заменяет записанный эталон — отдельное измерение с настоящими
 * импульсами, выдержкой и метками времени, на котором строятся A/B и проверка
 * продукта. Изученный фон отвечает на вопрос «похоже ли это на обычное для
 * места».
 *
 * ## Эффективный вес вместо экспозиции
 *
 * Профиль хранит медиану скорости счёта и разброс, а не накопленные импульсы,
 * поэтому строятся псевдосчёты `N = CPS × T_eff` с жёстким ограничением
 * `T_eff` ([MAX_EFFECTIVE_SECONDS]). Дальнейшая история делает оценку фона
 * устойчивее, но не увеличивает значимость сравнения до бесконечности: иначе
 * отклонение в доли процента объявлялось бы значимым.
 */
data class AdaptiveBackground(
    /** Обычная для этого места скорость счёта — медиана профиля, с⁻¹. */
    val cps: Float,
    /** Обычный разброс места: P10–P90 скорости счёта. */
    val low: Float,
    val high: Float,
    /** Сколько наблюдений вообще собрано, с — честная величина профиля. */
    val observedSeconds: Long,
) {
    /**
     * Собственный разброс МЕСТА, с⁻¹ — то, насколько счёт здесь гуляет сам по
     * себе: погода, радон, положение прибора в комнате.
     *
     * Оценивается из P10–P90 профиля: для симметричного распределения ширина
     * этого интервала ≈ 2,563 σ. Оценка грубая и намеренно робастная — она
     * нужна как ПОРЯДОК величины, а не как точная σ.
     */
    val spreadSigmaCps: Float
        get() = ((high - low) / P10_P90_TO_SIGMA).coerceAtLeast(0f)

    /**
     * Эффективная выдержка модели фона, с. **Не экспозиция измерения.**
     *
     * Ограничена дважды:
     *
     * 1. потолком [MAX_EFFECTIVE_SECONDS];
     * 2. собственным разбросом места. Пуассоновская погрешность часа
     *    наблюдений при 25 с⁻¹ — около 0,3 %, а счёт в месте гуляет на ±10 %
     *    (P10–P90 22–28). Вес модели ограничен тем, что следует из
     *    наблюдаемого разброса: `T_eff ≤ R / σ²` — счётная погрешность не
     *    должна оказаться меньше настоящей изменчивости места.
     *
     * Обычно связывает второе ограничение.
     */
    val effectiveExposureSeconds: Long
        get() {
            val sigma = spreadSigmaCps
            val bySpread = if (sigma > 0f) {
                (cps / (sigma * sigma)).toDouble().toLong()
            } else {
                MAX_EFFECTIVE_SECONDS
            }
            return observedSeconds
                .coerceAtMost(MAX_EFFECTIVE_SECONDS)
                .coerceAtMost(bySpread.coerceAtLeast(1L))
                .coerceAtLeast(0L)
        }

    /**
     * Эффективные псевдосчёты модели фона. **Не измеренные импульсы.**
     */
    val effectiveCounts: Double
        get() = cps.toDouble() * effectiveExposureSeconds

    /**
     * Окно сравнения из модели фона — с ЭФФЕКТИВНЫМ весом.
     *
     * Дальше по конвейеру оно неотличимо от окна записанного эталона, и это
     * намеренно: критерий сравнения в приложении один. Разница между эталоном
     * и изученным фоном живёт в ЧИСЛАХ этого окна и в словах на экране, а не в
     * отдельной ветке статистики.
     */
    fun referenceWindow(): CountWindow = CountWindow(
        counts = effectiveCounts,
        seconds = effectiveExposureSeconds.toDouble(),
        samples = effectiveExposureSeconds.toInt(),
    )

    /** Хватает ли собранного, чтобы вообще с чем-то сравнивать. */
    val usable: Boolean
        get() = cps > 0f && observedSeconds >= MIN_OBSERVED_SECONDS

    companion object {
        /**
         * Потолок эффективной выдержки, с.
         *
         * **Инженерный параметр**, а не свойство статистики: час наблюдений
         * даёт оценку фона, точность которой уже не ограничивает поиск, а
         * дальнейшая история уходит в устойчивость модели, а не в значимость
         * сравнения. Меняется числом здесь, и это меняет только уверенность
         * сравнения с изученным фоном — записанный эталон он не трогает.
         */
        const val MAX_EFFECTIVE_SECONDS = 3_600L

        /** Меньше этого наблюдений — сравнивать ещё не с чем. */
        const val MIN_OBSERVED_SECONDS = 600L

        /** Ширина P10–P90 в сигмах для симметричного распределения. */
        const val P10_P90_TO_SIGMA = 2.563f

        /**
         * Изученный фон места по профилю; null — профиль ещё не собран или
         * собран не по скорости счёта.
         */
        fun of(baseline: Baseline?): AdaptiveBackground? {
            val current = baseline ?: return null
            if (!current.cpsMedian.isFinite() || current.cpsMedian <= 0f) return null
            return AdaptiveBackground(
                cps = current.cpsMedian,
                low = current.cpsLow,
                high = current.cpsHigh,
                observedSeconds = current.accumulatedSeconds,
            ).takeIf { it.usable }
        }
    }
}

/**
 * Чем Поиск сравнивает сейчас.
 *
 * Два источника намеренно разные и не подменяют друг друга: записанный эталон
 * несёт настоящие импульсы и выдержку, изученный фон — ограниченный
 * эффективный вес. Экран показывает РАЗНЫЕ слова для них, потому что это
 * разные утверждения о надёжности сравнения.
 */
sealed interface SearchReference {

    /** Человек записал эталон: настоящие импульсы, выдержка, метки времени. */
    data class Recorded(val record: BackgroundRecord) : SearchReference

    /** Приложение изучило фон места само. */
    data class Learned(val background: AdaptiveBackground) : SearchReference

    /** Сравнивать не с чем. */
    data object None : SearchReference

    val rateCps: Float?
        get() = when (this) {
            is Recorded -> record.cps
            is Learned -> background.cps
            None -> null
        }
}

object SearchReferences {

    /**
     * Что взять за фон. Записанный эталон главнее: он несёт настоящие счёты.
     * Изученный фон вступает, когда эталона нет или он больше не годится
     * (другой прибор, другое место, устарел).
     */
    fun choose(
        record: BackgroundRecord?,
        check: BackgroundCheck?,
        learned: AdaptiveBackground?,
    ): SearchReference = when {
        record != null && check == BackgroundCheck.USABLE -> SearchReference.Recorded(record)
        learned != null -> SearchReference.Learned(learned)
        record != null -> SearchReference.Recorded(record)
        else -> SearchReference.None
    }
}
