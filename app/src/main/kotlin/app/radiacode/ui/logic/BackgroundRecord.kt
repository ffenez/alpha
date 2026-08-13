package app.radiacode.ui.logic

import app.radiacode.analysis.AlgorithmVersions
import app.radiacode.analysis.CountWindow
import app.radiacode.analysis.Dispersion
import app.radiacode.analysis.RateComparison
import app.radiacode.data.JsonMap
import app.radiacode.ui.text.BackgroundCardRu
import app.radiacode.ui.text.BackgroundCardStrings
import app.radiacode.ui.text.SearchRu
import app.radiacode.ui.text.SearchStrings

/**
 * The recorded background of Поиск, **with everything needed to judge it**
 * (search redesign §6).
 *
 * Storing a bare mean CPS — what this used to be — makes three questions
 * unanswerable: how long was it averaged (so the statistical test cannot weigh
 * it), when and where was it taken (so it can never be called stale), and did
 * the stream behave while it was taken (so a background measured while walking
 * looks exactly like one measured standing still). All three are the difference
 * between a reference and a number.
 *
 * The window itself is the [CountWindow] the comparison consumes directly, so
 * the stored reference and the tested reference are the same object — nothing
 * is re-derived at read time.
 */
data class BackgroundRecord(
    /** Counts, exposure and scatter of the measurement. */
    val window: CountWindow,
    /** Wall clock when the measurement finished. */
    val atMillis: Long,
    /** Readings the run asked for; fewer means it was cut short. */
    val targetSamples: Int,
    /** Profile the measurement belongs to; null = no context at the time. */
    val profileId: Long?,
    /** Human label of that profile, kept for the sheet after a rename. */
    val profileName: String?,
    /** Instrument the measurement came from; another device is another background. */
    val deviceSerial: String?,
    /** Version of the statistics this reference was recorded for (spec §22). */
    val algorithmVersion: Int = AlgorithmVersions.RATE_COMPARISON,
) {

    /** The reference rate itself, s⁻¹. */
    val cps: Float get() = window.ratePerSecond.toFloat()

    /** 1σ of the reference rate, s⁻¹ — the uncertainty of the background. */
    val sigma: Float get() = window.poissonSigma.toFloat()

    fun ageMillis(nowMillis: Long): Long = (nowMillis - atMillis).coerceAtLeast(0L)

    /**
     * What the measurement itself looked like. This is about the *recording*,
     * not about the radiation: a gappy or restless background is a bad ruler
     * whatever it measured.
     */
    val quality: BackgroundQuality
        get() {
            val gapSeconds = window.gapSeconds
            val fano = window.fanoFactor
            return when {
                window.samples < targetSamples -> BackgroundQuality.SHORT
                gapSeconds > MAX_GAP_SECONDS -> BackgroundQuality.GAPPY
                fano != null && fano > RateComparison.FANO_HIGH -> BackgroundQuality.RESTLESS
                else -> BackgroundQuality.GOOD
            }
        }

    /** Dispersion verdict of the recording, for the research layer. */
    val dispersion: Dispersion
        get() {
            val fano = window.fanoFactor ?: return Dispersion.UNKNOWN
            return when {
                fano < RateComparison.FANO_LOW -> Dispersion.UNDERDISPERSED
                fano > RateComparison.FANO_HIGH -> Dispersion.OVERDISPERSED
                else -> Dispersion.POISSON_LIKE
            }
        }

    /**
     * Whether this reference still describes the situation the user is in.
     *
     * A background is never *silently* replaced or extended (redesign §6, §12):
     * the worst failure of a search mode is to slowly absorb the source into
     * the background while walking towards it. So this only ever reports — the
     * decision to re-measure stays with the user.
     */
    fun check(
        nowMillis: Long,
        activeProfileId: Long?,
        deviceSerial: String?,
    ): BackgroundCheck = when {
        deviceSerial != null && this.deviceSerial != null && deviceSerial != this.deviceSerial ->
            BackgroundCheck.DEVICE_CHANGED
        // A null active profile means «context unknown right now», which is not
        // evidence that the place changed — only a different known profile is.
        activeProfileId != null && profileId != null && activeProfileId != profileId ->
            BackgroundCheck.PROFILE_CHANGED
        quality != BackgroundQuality.GOOD -> BackgroundCheck.LOW_QUALITY
        ageMillis(nowMillis) > FRESH_MILLIS -> BackgroundCheck.AGED
        else -> BackgroundCheck.USABLE
    }

    fun encode(): String = JsonMap.of(
        "counts" to window.counts,
        "seconds" to window.seconds,
        "samples" to window.samples,
        "sumRate" to window.sumRate,
        "sumRateSquares" to window.sumRateSquares,
        "gapSeconds" to window.gapSeconds,
        "atMillis" to atMillis,
        "targetSamples" to targetSamples,
        "profileId" to profileId,
        "profileName" to profileName,
        "deviceSerial" to deviceSerial,
        "algorithmVersion" to algorithmVersion,
    )

    companion object {

        /**
         * How long a recorded background is treated as describing «here, now».
         *
         * **Engineering parameter.** Half an hour is short enough that an
         * ordinary move — another room, outdoors, a different building — lands
         * on the far side of it, and long enough that a single sweep of a flat
         * is never interrupted by a re-measurement request.
         */
        const val FRESH_MILLIS = 30 * 60 * 1000L

        /**
         * Lost stream time a reference may contain and still be called good.
         * **Engineering parameter**: a couple of missed records is BLE jitter,
         * more than that and the averaging interval has a real hole in it.
         */
        const val MAX_GAP_SECONDS = 3.0

        fun decode(raw: String?): BackgroundRecord? {
            val map = JsonMap.decode(raw)
            if (map.isEmpty()) return null
            val counts = map["counts"]?.toDoubleOrNull() ?: return null
            val seconds = map["seconds"]?.toDoubleOrNull() ?: return null
            val samples = map["samples"]?.toIntOrNull() ?: return null
            val atMillis = map["atMillis"]?.toLongOrNull() ?: return null
            if (counts < 0.0 || seconds <= 0.0 || samples <= 0) return null
            return BackgroundRecord(
                window = CountWindow(
                    counts = counts,
                    seconds = seconds,
                    samples = samples,
                    sumRate = map["sumRate"]?.toDoubleOrNull() ?: 0.0,
                    sumRateSquares = map["sumRateSquares"]?.toDoubleOrNull() ?: 0.0,
                    gapSeconds = map["gapSeconds"]?.toDoubleOrNull() ?: 0.0,
                ),
                atMillis = atMillis,
                targetSamples = map["targetSamples"]?.toIntOrNull() ?: samples,
                profileId = map["profileId"]?.toLongOrNull(),
                profileName = map["profileName"],
                deviceSerial = map["deviceSerial"],
                algorithmVersion = map["algorithmVersion"]?.toIntOrNull()
                    ?: AlgorithmVersions.RATE_COMPARISON,
            )
        }
    }
}

/**
 * How the background *recording* went — a property of the measurement.
 *
 * Подпись живёт в каталоге области ([SearchBaseline.qualityLabel]), а не в
 * самом перечислении: качество замера читает человек, а язык интерфейса
 * выбирается в настройках.
 */
enum class BackgroundQuality {
    GOOD,

    /** The run ended before its target length. */
    SHORT,

    /** The stream lost time inside the averaging interval. */
    GAPPY,

    /** The readings scattered wider than counting statistics — the instrument
     *  was probably moving, or the field was not steady. */
    RESTLESS,
}

/** Whether a stored background may still be used as the reference right now. */
enum class BackgroundCheck {
    USABLE,

    /** Older than [BackgroundRecord.FRESH_MILLIS]. */
    AGED,

    /** Measured under a different profile than the one active now. */
    PROFILE_CHANGED,

    /** Measured with a different instrument. */
    DEVICE_CHANGED,

    /** The recording itself was short, gappy or restless. */
    LOW_QUALITY,
}

/**
 * Карточка записанного фона в том виде, в каком её читает человек (ТЗ §10).
 *
 * Модель, а не набор строк в композиции: что стоит на рабочем экране, а что
 * уехало на второй уровень, — это решение, и оно проверяется тестом, а не
 * взглядом на вёрстку.
 */
data class BackgroundCardModel(
    /** Главная строка: «Фон: 24,1 имп/с» либо «Фон не записан». */
    val level: String,
    /** «Записан в 15:04 · измерение 45 с»; null — записи ещё нет. */
    val basis: String?,
    /** Одна строка с НАЗВАННОЙ причиной непригодности; null — фон годится. */
    val reason: String?,
    /** Подпись кнопки: «Обновить фон» либо «Замерить фон · 45 с». */
    val action: String,
    /** Кнопка ведущая, только пока сравнивать не с чем. */
    val actionPrimary: Boolean,
    /** Второй уровень («i»): полное объяснение причины и параметры замера. */
    val details: List<String>,
) {
    /** Фон пригоден: карточка не спорит с человеком и ничего не требует. */
    val usable: Boolean get() = reason == null
}

/**
 * What Поиск does on open (redesign §5, §9): a usable background means the
 * search starts immediately, anything else means *proposing* a 45 s
 * measurement — never demanding one, and never starting one by itself while
 * the user may already be holding the instrument near something.
 */
object SearchBaseline {

    /** One line naming why a re-measurement is being proposed. */
    fun proposal(
        check: BackgroundCheck,
        record: BackgroundRecord?,
        t: SearchStrings = SearchRu,
    ): String? = when (check) {
        BackgroundCheck.USABLE -> null
        // Приложение не знает, что человек делал эти полчаса, и не должно
        // сочинять за него сценарий: оно называет факт и условие сравнения.
        BackgroundCheck.AGED -> t.proposalAged
        BackgroundCheck.PROFILE_CHANGED -> t.proposalProfileChanged(record?.profileName)
        BackgroundCheck.DEVICE_CHANGED -> t.proposalDeviceChanged
        BackgroundCheck.LOW_QUALITY -> when (record?.quality) {
            BackgroundQuality.SHORT -> t.proposalShort
            BackgroundQuality.GAPPY -> t.proposalGappy
            BackgroundQuality.RESTLESS -> t.proposalRestless
            else -> t.proposalUnusable
        }
    }

    /** Short status of the stored reference for the background card. */
    fun statusLine(check: BackgroundCheck, t: SearchStrings = SearchRu): String = when (check) {
        BackgroundCheck.USABLE -> t.statusUsable
        BackgroundCheck.AGED -> t.statusAged
        BackgroundCheck.PROFILE_CHANGED -> t.statusProfileChanged
        BackgroundCheck.DEVICE_CHANGED -> t.statusDeviceChanged
        BackgroundCheck.LOW_QUALITY -> t.statusLowQuality
    }

    /**
     * Короткая карточка записанного фона (ТЗ §10).
     *
     * Первый уровень отвечает на три вопроса и молчит обо всём остальном:
     * какой фон записан, когда и как долго его мерили, и — если он больше не
     * годится — ОДНОЙ строкой, что именно с ним не так. Абзац, объясняющий
     * почему, никуда не делся: он лежит в [BackgroundCardModel.details] и
     * показывается под «i».
     *
     * @param rateText готовое число скорости счёта (форматирование единиц —
     *   дело экрана, а не этой функции)
     * @param timeOfDay момент записи в часовом поясе пользователя
     */
    fun card(
        record: BackgroundRecord?,
        check: BackgroundCheck,
        rateText: String,
        timeOfDay: String,
        targetSeconds: Int,
        c: BackgroundCardStrings = BackgroundCardRu,
        t: SearchStrings = SearchRu,
    ): BackgroundCardModel {
        if (record == null) {
            return BackgroundCardModel(
                level = c.noRecord,
                basis = null,
                reason = null,
                action = c.measure(targetSeconds),
                actionPrimary = true,
                details = listOf(c.noRecordHint(targetSeconds)),
            )
        }
        val quality = record.quality
        val reason = when (check) {
            BackgroundCheck.USABLE -> null
            BackgroundCheck.AGED -> c.agedLine
            BackgroundCheck.PROFILE_CHANGED -> c.profileChangedLine(record.profileName)
            BackgroundCheck.DEVICE_CHANGED -> c.deviceChangedLine
            BackgroundCheck.LOW_QUALITY -> when (quality) {
                BackgroundQuality.SHORT -> c.shortLine
                BackgroundQuality.GAPPY -> c.gappyLine
                BackgroundQuality.RESTLESS -> c.restlessLine
                BackgroundQuality.GOOD -> c.agedLine
            }
        }
        return BackgroundCardModel(
            level = c.level(rateText),
            basis = c.recordedAt(timeOfDay, record.window.seconds.toInt()),
            reason = reason,
            action = c.refresh,
            // Пока фон годится, работа человека — ходить с прибором, а не
            // нажимать эту кнопку.
            actionPrimary = check != BackgroundCheck.USABLE,
            details = listOfNotNull(
                // Развёрнутое объяснение причины — тот же текст, что и раньше,
                // но уровнем ниже.
                proposal(check, record, t),
                t.backgroundDetail(
                    samples = record.window.samples,
                    seconds = record.window.seconds.toInt(),
                    quality = qualityLabel(quality, t),
                    profile = record.profileName,
                ),
            ),
        )
    }

    /** Как называется качество самого замера — свойство записи, не излучения. */
    fun qualityLabel(quality: BackgroundQuality, t: SearchStrings = SearchRu): String =
        when (quality) {
            BackgroundQuality.GOOD -> t.qualityGood
            BackgroundQuality.SHORT -> t.qualityShort
            BackgroundQuality.GAPPY -> t.qualityGappy
            BackgroundQuality.RESTLESS -> t.qualityRestless
        }
}
