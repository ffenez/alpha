package app.radiacode.ui.logic

import app.radiacode.data.SessionAdmission
import app.radiacode.data.SessionSummary
import app.radiacode.data.db.RangeStats

/**
 * Подряд идущие записи одного места, показанные как ОДНО измерение.
 *
 * ## Зачем
 *
 * Полевой отчёт: за три часа дома журнал показал восемь записей «Дом» — 4, 8,
 * 58, 17, 12, 32, 33 и 19 минут. Человек всё это время никуда не уходил и
 * ничего не начинал заново; на куски запись рвали разрывы связи и перезапуски
 * службы. Границы сессий исправлены в самой службе, но уже записанное так и
 * останется восемью строками, если не склеить их при показе.
 *
 * Склейка — ПРЕДСТАВЛЕНИЕ, а не переписывание журнала: строки в базе не
 * трогаются, у группы остаются все их идентификаторы, и уборка с переносом
 * профиля действуют на все сразу.
 *
 * ## Чего склейка не делает
 *
 * Не сливает разные места и не прячет перерыв: дыра внутри группы названа
 * числом и временем, а группа из одной записи ничем не отличается от прежней
 * строки. Числа складываются точно — среднее взвешено числом измерений, а не
 * длительностью, потому что именно измерения его и образуют.
 */
data class SessionGroup(
    /** Все склеенные записи, новая первой. */
    val ids: List<Long>,
    val profileId: Long?,
    val profileName: String?,
    val startedAt: Long,
    /** null — измерение идёт прямо сейчас. */
    val endedAt: Long?,
    val stats: RangeStats,
    val doseMicroSv: Double,
    val hasSpectrum: Boolean,
    val hasTrack: Boolean,
    val hasFlight: Boolean,
    val admission: SessionAdmission,
    /** Сколько записей склеено; 1 — обычная одиночная. */
    val pieces: Int,
    /** Суммарный перерыв внутри группы, секунды. */
    val gapSeconds: Long,
) {
    val running: Boolean get() = endedAt == null
}

object SessionGroups {

    /**
     * Склеивает соседние записи ОДНОГО профиля, между которыми меньше
     * [graceMillis].
     *
     * Порядок входа — от новой к старой, как в журнале; порядок выхода такой
     * же. Ни одна запись не теряется и не меняет места.
     */
    fun merge(
        sessions: List<SessionSummary>,
        graceMillis: Long,
        nowMillis: Long,
    ): List<SessionGroup> {
        val out = mutableListOf<SessionGroup>()
        var current: MutableList<SessionSummary> = mutableListOf()

        fun flush() {
            if (current.isEmpty()) return
            out += fold(current, nowMillis)
            current = mutableListOf()
        }

        for (session in sessions) {
            val previous = current.lastOrNull()
            val joins = previous != null &&
                previous.profileId == session.profileId &&
                // Список идёт от новой записи к старой: предыдущая началась
                // ПОЗЖЕ, и разрыв — от конца этой до начала той.
                previous.startedAt - (session.endedAt ?: nowMillis) <= graceMillis
            if (!joins) flush()
            current += session
        }
        flush()
        return out
    }

    private fun fold(pieces: List<SessionSummary>, nowMillis: Long): SessionGroup {
        val newest = pieces.first()
        val oldest = pieces.last()
        val samples = pieces.sumOf { it.stats.sampleCount }
        // Среднее взвешено числом измерений: сессия из тридцати отсчётов не
        // весит столько же, сколько сессия из трёх тысяч.
        val weightedDose = pieces.sumOf { p ->
            (p.stats.avgDoseRate?.toDouble() ?: 0.0) * p.stats.sampleCount
        }
        val weightedCount = pieces.sumOf { p ->
            (p.stats.avgCountRate?.toDouble() ?: 0.0) * p.stats.sampleCount
        }
        val stats = RangeStats(
            sampleCount = samples,
            avgDoseRate = if (samples > 0) (weightedDose / samples).toFloat() else null,
            minDoseRate = pieces.mapNotNull { it.stats.minDoseRate }.minOrNull(),
            maxDoseRate = pieces.mapNotNull { it.stats.maxDoseRate }.maxOrNull(),
            avgCountRate = if (samples > 0) (weightedCount / samples).toFloat() else null,
            maxCountRate = pieces.mapNotNull { it.stats.maxCountRate }.maxOrNull(),
        )
        var gap = 0L
        for (i in 0 until pieces.size - 1) {
            val later = pieces[i]
            val earlier = pieces[i + 1]
            gap += (later.startedAt - (earlier.endedAt ?: nowMillis)).coerceAtLeast(0L)
        }
        return SessionGroup(
            ids = pieces.map { it.id },
            profileId = newest.profileId,
            profileName = newest.profileName,
            startedAt = oldest.startedAt,
            endedAt = newest.endedAt,
            stats = stats,
            doseMicroSv = pieces.sumOf { it.doseMicroSv },
            hasSpectrum = pieces.any { it.hasSpectrum },
            hasTrack = pieces.any { it.hasTrack },
            hasFlight = pieces.any { it.hasFlight },
            admission = SessionAdmission(
                admittedSeconds = pieces.sumOf { it.admission.admittedSeconds },
                exclusions = pieces.flatMap { it.admission.exclusions }
                    .groupBy { it.reason }
                    .map { (reason, list) ->
                        list.first().copy(seconds = list.sumOf { it.seconds })
                    }
                    .sortedByDescending { it.seconds },
            ),
            pieces = pieces.size,
            gapSeconds = gap / 1000L,
        )
    }
}
