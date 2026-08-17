package app.alpha.analysis

import app.alpha.protocol.Spectrum

/**
 * Эпоха накопления — непрерывный отрезок жизни спектра между сбросами.
 *
 * Зачем она нужна: спектр прибора накопительный, и разность двух снимков
 * ОДНОЙ эпохи — это спектр ровно за промежуток между ними. Через сброс такое
 * вычитание бессмысленно: счёты начались заново, и разность даёт то
 * отрицательные каналы, то правдоподобную чепуху.
 *
 * Как эпоха определяется (ADR 008). Приложение не спрашивает прибор «была ли
 * перезагрузка» — такого вопроса в протоколе нет. Оно смотрит на сам спектр:
 * накопление обязано расти. Если длительность или счёты стали МЕНЬШЕ, чем в
 * прошлый раз, — накопление началось заново, чем бы это ни было вызвано:
 * сбросом с экрана, кнопкой на приборе, перезагрузкой. Смена прибора — тоже
 * новая эпоха, и это единственный случай, который виден не из чисел.
 */
object SpectrumEpoch {

    /** Что известно о прошлом опросе — этого достаточно, чтобы решить. */
    data class Mark(
        val epochId: Long,
        val deviceSerial: String?,
        val durationSeconds: Long,
        val totalCounts: Long,
    )

    /** Метка строкой — чтобы пережить перезапуск процесса. */
    fun encode(mark: Mark): String = listOf(
        mark.epochId,
        mark.deviceSerial.orEmpty(),
        mark.durationSeconds,
        mark.totalCounts,
    ).joinToString("|")

    fun decode(encoded: String?): Mark? {
        val parts = encoded?.split('|') ?: return null
        if (parts.size != 4) return null
        val epochId = parts[0].toLongOrNull() ?: return null
        val duration = parts[2].toLongOrNull() ?: return null
        val total = parts[3].toLongOrNull() ?: return null
        return Mark(
            epochId = epochId,
            deviceSerial = parts[1].ifEmpty { null },
            durationSeconds = duration,
            totalCounts = total,
        )
    }

    fun totalCounts(spectrum: Spectrum): Long =
        spectrum.counts.fold(0L) { sum, value -> sum + value }

    /**
     * Метка эпохи для нового опроса.
     *
     * @param previous метка прошлого опроса; null — первый опрос за всё время.
     * @param newEpochId идентификатор, который присвоится НОВОЙ эпохе (обычно
     *   момент времени: он монотонен и не требует счётчика в базе).
     */
    fun mark(
        previous: Mark?,
        spectrum: Spectrum,
        deviceSerial: String?,
        newEpochId: Long,
    ): Mark {
        val total = totalCounts(spectrum)
        val continues = previous != null &&
            previous.deviceSerial == deviceSerial &&
            spectrum.durationSeconds >= previous.durationSeconds &&
            total >= previous.totalCounts
        return Mark(
            epochId = if (continues) previous!!.epochId else newEpochId,
            deviceSerial = deviceSerial,
            durationSeconds = spectrum.durationSeconds,
            totalCounts = total,
        )
    }
}

/**
 * Спектр за промежуток: разность двух снимков одного накопления.
 *
 * Это ответ на вопрос «что было namerено ИМЕННО СЕЙЧАС», тогда как сам снимок
 * отвечает «что накопилось с начала эпохи» — и на 126-часовом накоплении
 * второе полностью заслоняет первое.
 *
 * Все проверки — отказные: любое сомнение означает [Delta.Unavailable] с
 * названной причиной, а не вычисленный результат. Придуманная разность здесь
 * опаснее отсутствующей: на ней строятся поиск источника, A/B и измерение
 * продукта.
 */
object SpectrumDelta {

    /** Снимок в том виде, в каком его умеет вычитать этот движок. */
    data class Snapshot(
        val counts: List<Int>,
        val durationSeconds: Long,
        val a0: Float,
        val a1: Float,
        val a2: Float,
        val deviceSerial: String?,
        val epochId: Long?,
    )

    sealed interface Delta {
        data class Available(
            val counts: List<Int>,
            val durationSeconds: Long,
            val a0: Float,
            val a1: Float,
            val a2: Float,
        ) : Delta {
            val totalCounts: Long get() = counts.fold(0L) { sum, value -> sum + value }
            val countRate: Double
                get() = if (durationSeconds > 0) totalCounts.toDouble() / durationSeconds else 0.0
        }

        /** Почему разности нет. Причина обязана дойти до экрана. */
        data class Unavailable(val reason: Reason) : Delta
    }

    enum class Reason {
        /** У одного из снимков нет провенанса — сравнивать не с чем. */
        NO_PROVENANCE,
        DIFFERENT_DEVICE,
        DIFFERENT_EPOCH,
        DIFFERENT_CALIBRATION,
        DIFFERENT_CHANNELS,
        NOT_LATER,
        NOT_MONOTONIC,
    }

    /** Допуск сравнения калибровок: у float одинаковые числа бывают разными. */
    const val CALIBRATION_TOLERANCE = 1e-4f

    fun of(from: Snapshot, to: Snapshot): Delta {
        if (from.epochId == null || to.epochId == null) {
            return Delta.Unavailable(Reason.NO_PROVENANCE)
        }
        if (from.deviceSerial == null || from.deviceSerial != to.deviceSerial) {
            return Delta.Unavailable(Reason.DIFFERENT_DEVICE)
        }
        if (from.epochId != to.epochId) return Delta.Unavailable(Reason.DIFFERENT_EPOCH)
        if (from.counts.size != to.counts.size) return Delta.Unavailable(Reason.DIFFERENT_CHANNELS)
        if (!sameCalibration(from, to)) return Delta.Unavailable(Reason.DIFFERENT_CALIBRATION)
        val seconds = to.durationSeconds - from.durationSeconds
        if (seconds <= 0) return Delta.Unavailable(Reason.NOT_LATER)

        val counts = ArrayList<Int>(to.counts.size)
        for (index in to.counts.indices) {
            val difference = to.counts[index] - from.counts[index]
            // Ни один канал накопительного спектра не может убыть. Убыл —
            // значит между снимками что-то произошло, чего мы не заметили, и
            // «поправить» это нулём значило бы скрыть событие.
            if (difference < 0) return Delta.Unavailable(Reason.NOT_MONOTONIC)
            counts += difference
        }
        return Delta.Available(
            counts = counts,
            durationSeconds = seconds,
            a0 = to.a0,
            a1 = to.a1,
            a2 = to.a2,
        )
    }

    private fun sameCalibration(from: Snapshot, to: Snapshot): Boolean =
        kotlin.math.abs(from.a0 - to.a0) <= CALIBRATION_TOLERANCE &&
            kotlin.math.abs(from.a1 - to.a1) <= CALIBRATION_TOLERANCE &&
            kotlin.math.abs(from.a2 - to.a2) <= CALIBRATION_TOLERANCE
}
