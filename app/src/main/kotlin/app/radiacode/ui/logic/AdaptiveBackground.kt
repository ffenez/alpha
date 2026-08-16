package app.radiacode.ui.logic

import app.radiacode.baseline.Baseline

/**
 * Фон, который приложение изучило само, — для повседневного поиска.
 *
 * ## Зачем он
 *
 * Чтобы начать искать, не нужно было каждый раз нажимать «Записать фон».
 * Обычный фон МЕСТА уже собирается непрерывно: медиана, P10–P90, MAD, с
 * правилами допуска (не учить во время эксперимента, не учить после
 * отклонения, не учить чужой прибор). Поиску остаётся им воспользоваться.
 *
 * ## Чем он НЕ является
 *
 * Он не заменяет записанный эталон. Эталон — это отдельное измерение с
 * настоящими импульсами, выдержкой и метками времени; на нём строятся A/B,
 * проверка продукта и всё, где нужно статистически честное сравнение.
 * Изученный фон отвечает на другой вопрос — «похоже ли то, что я вижу
 * сейчас, на обычное для этого места», — и этого достаточно для поиска.
 *
 * ## Почему у него ЭФФЕКТИВНЫЙ вес, а не экспозиция
 *
 * Профиль хранит медиану скорости счёта и разброс, а не накопленные импульсы:
 * восстановить из них реальные счёты невозможно. Поэтому строятся ПСЕВДОСЧЁТЫ
 * `N = CPS × T_eff`, и `T_eff` жёстко ограничен [MAX_EFFECTIVE_SECONDS].
 *
 * Ограничение — не формальность. Профиль, обучавшийся неделю, нельзя
 * превращать в «недельную экспозицию»: дальнейшая история делает оценку фона
 * УСТОЙЧИВЕЕ, но не увеличивает статистическую значимость сравнения до
 * бесконечности — иначе любое отклонение в доли процента объявлялось бы
 * значимым. Поэтому величины и названы эффективными: это вес модели фона, а
 * не проведённое измерение.
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
     * Эффективная выдержка модели фона, с. **Не экспозиция измерения.**
     */
    val effectiveExposureSeconds: Long
        get() = observedSeconds.coerceAtMost(MAX_EFFECTIVE_SECONDS).coerceAtLeast(0L)

    /**
     * Эффективные псевдосчёты модели фона. **Не измеренные импульсы.**
     */
    val effectiveCounts: Double
        get() = cps.toDouble() * effectiveExposureSeconds

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
     * Что взять за фон.
     *
     * Записанный эталон главнее: его записывали ради этого измерения, и он
     * несёт настоящие счёты. Изученный фон вступает, когда эталона нет вовсе
     * или он больше не годится (другой прибор, другое место, устарел) — тогда
     * молча сравнивать с ним нельзя, а отказаться от поиска незачем.
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
