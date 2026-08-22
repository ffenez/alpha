package app.alpha.analysis.evidence

/**
 * Когда измеренная модель разрешения принимается САМА.
 *
 * ## Почему это можно делать без человека
 *
 * Модель разрешения — внутренний параметр анализа: она задаёт ожидаемую ширину
 * линии, то есть размеры окон поиска и допуски совпадения. Она НЕ меняет
 * показанную энергию и не переписывает калибровку прибора — в отличие от
 * поправки шкалы ([app.alpha.analysis.ScaleCorrection]), которая двигает
 * подписи и потому остаётся за человеком. Подгонка сама себя проверяет
 * ([ResolutionFitting]: не меньше трёх линий, размах не меньше 500 кэВ,
 * ширина не убывает с энергией) и отказывается там, где данных мало, поэтому
 * принять её автоматически — то же самое, что перестать спрашивать разрешения
 * на уже проверенный результат.
 *
 * ## Чего автоматика не делает
 *
 * Не перебивает выбор человека. Если модель принята вручную, фон её не
 * трогает — кроме случая, когда подключён ДРУГОЙ прибор: чужие коэффициенты
 * описывают чужой кристалл, и держаться за них хуже, чем измерить заново.
 */
object ResolutionAdoption {

    /**
     * Что записать в настройки после фонового разбора.
     *
     * @param fit результат подгонки по измеренным линиям.
     * @param serial серийник прибора, по спектрам которого собран материал;
     *   null — прибор не подключён и не опознан.
     * @param stored то, что уже принято (вручную или автоматически).
     * @return модель, которую следует записать; null — оставить как есть.
     */
    fun decide(
        fit: ResolutionFitOutcome,
        serial: String?,
        stored: AcceptedResolution?,
        nowMillis: Long,
        algorithmVersion: Int,
    ): AcceptedResolution? {
        val fitted = (fit as? ResolutionFitOutcome.Fitted)?.fit ?: return null
        val next = AcceptedResolution(
            a = fitted.a,
            b = fitted.b,
            c = fitted.c,
            deviceSerial = serial,
            acceptedAtMillis = nowMillis,
            points = fitted.points.size,
            lowestKeV = fitted.extrapolatedBelowKeV,
            highestKeV = fitted.extrapolatedAboveKeV,
            algorithmVersion = algorithmVersion,
            automatic = true,
        )
        if (stored == null) return next
        // Прибор сменился — прежние коэффициенты описывают другой кристалл.
        val sameDevice = stored.deviceSerial == serial
        if (!sameDevice) return next
        // Человек выбрал сам: фон молчит, пока он не откажется от своего выбора.
        if (!stored.automatic) return null
        // Свежая подгонка принимается, если она опирается на большее число
        // линий (шире охват шкалы) либо если прежней уже столько, что прибор
        // за это время мог уехать по усилению.
        val older = nowMillis - stored.acceptedAtMillis >= REFRESH_MILLIS
        val richer = next.points > stored.points
        val newerMath = algorithmVersion > stored.algorithmVersion
        return if (older || richer || newerMath) next else null
    }

    /**
     * Через сколько принятая автоматически модель заменяется свежей —
     * **инженерный параметр**. Неделя: усиление сцинтилляционного тракта
     * плывёт с температурой и старением, а материал за это время успевает
     * обновиться (снимок раз в 10 минут, окно разбора 30 суток).
     */
    const val REFRESH_MILLIS = 7L * 24L * 3_600_000L
}
