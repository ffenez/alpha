package app.radiacode.data.export

import app.radiacode.analysis.AlgorithmVersions
import app.radiacode.data.JsonMap
import app.radiacode.data.db.SpectrumSnapshotEntity
import java.util.Locale

/**
 * Processing metadata every export carries (spec §22: «Экспорт указывает
 * нормализацию, background method, calibration metadata и версии
 * алгоритмов»).
 *
 * The metadata is *derived from the row itself*, not passed in by the screen:
 * a snapshot knows its calibration, and a derived snapshot carries its
 * `analysisMeta` stamp. That way no export path can forget it.
 *
 * Rendered as plain lines — RC-XML puts them into `SampleInfo/Note`, N42 into
 * `<Remark>` elements, and the experiment report into its header. Nothing here
 * is machine-parsed by this app; the point is that a file opened in two years,
 * possibly in InterSpec, still says how its numbers were made.
 */
data class ProcessingMetadata(
    /** How counts were normalized before any comparison, or that they were not. */
    val normalization: String,
    /** How background was handled: «не вычитался» or the exact expression. */
    val backgroundMethod: String,
    /** Energy calibration and channel count. */
    val calibration: String,
    /** Algorithm keys → versions, e.g. `spectrum_compare` → 1. */
    val algorithmVersions: Map<String, Int>,
    /** Anything specific to this artefact (source snapshots, live times…). */
    val extra: List<String> = emptyList(),
    /** App version name, when the caller knows it. */
    val appVersion: String? = null,
) {

    fun lines(): List<String> = buildList {
        add("нормализация: $normalization")
        add("фон: $backgroundMethod")
        add("калибровка: $calibration")
        add(
            "версии алгоритмов: " + (
                algorithmVersions.entries
                    .joinToString(" · ") { "${it.key} v${it.value}" }
                    .ifEmpty { "нет производных расчётов" }
                ),
        )
        addAll(extra)
        appVersion?.let { add("приложение: $it") }
    }

    fun asText(): String = lines().joinToString("\n")

    companion object {

        const val NORMALIZATION_RAW = "нет — сохранён сырой счёт по каналам (counts/channel)"
        const val NORMALIZATION_RATE = "counts/s per channel (rᵢ = Nᵢ/t)"
        const val BACKGROUND_NONE = "не вычитался"
        const val BACKGROUND_TIME_SCALED =
            "вычтен с нормировкой по времени: net = G − B·(t_G/t_B), " +
                "σ_net = √(G + B·(t_G/t_B)²) [IAEA R4]"

        /** «E(кэВ) = a0 + a1·ch + a2·ch², a0=…, a1=…, a2=…, каналов N». */
        fun calibrationLine(a0: Float, a1: Float, a2: Float, channelCount: Int): String =
            String.format(
                Locale.ROOT,
                "E(кэВ) = a0 + a1·ch + a2·ch², a0=%.4f, a1=%.5f, a2=%.3e, каналов %d",
                a0,
                a1,
                a2,
                channelCount,
            )

        /** Keys of [app.radiacode.data.JsonMap] stamps this class understands. */
        const val KEY_METHOD = "method"
        const val KEY_NORMALIZATION = "normalization"
        const val KEY_BACKGROUND = "background"

        /** Space-separated [AlgorithmVersions.all] keys. */
        const val KEY_ALGORITHMS = "algorithms"

        private val KNOWN_KEYS = setOf(KEY_METHOD, KEY_NORMALIZATION, KEY_BACKGROUND, KEY_ALGORITHMS)

        /** The stamp a derived snapshot stores in `spectra.analysisMeta`. */
        fun stamp(
            method: String,
            algorithms: List<String>,
            normalization: String = NORMALIZATION_RAW,
            background: String = BACKGROUND_NONE,
            extra: Map<String, String> = emptyMap(),
        ): String {
            val values = LinkedHashMap<String, String>()
            values[KEY_METHOD] = method
            values[KEY_ALGORITHMS] = algorithms.joinToString(" ")
            values[KEY_NORMALIZATION] = normalization
            values[KEY_BACKGROUND] = background
            values.putAll(extra)
            return JsonMap.encode(values)
        }

        /**
         * Metadata of a stored snapshot. Raw device/import rows report «no
         * processing»; derived rows unpack their `analysisMeta` stamp.
         */
        fun of(
            entity: SpectrumSnapshotEntity,
            appVersion: String? = null,
        ): ProcessingMetadata {
            val meta = JsonMap.decode(entity.analysisMeta)
            val extra = buildList {
                add("время накопления: ${entity.durationSeconds} с")
                meta[KEY_METHOD]?.let { add("метод получения: $it") }
                meta.forEach { (key, value) -> if (key !in KNOWN_KEYS) add("$key: $value") }
            }
            val algorithms = meta[KEY_ALGORITHMS].orEmpty()
                .split(' ')
                .mapNotNull { key -> AlgorithmVersions.all[key]?.let { key to it } }
                .toMap()
            return ProcessingMetadata(
                normalization = meta[KEY_NORMALIZATION] ?: NORMALIZATION_RAW,
                backgroundMethod = meta[KEY_BACKGROUND] ?: BACKGROUND_NONE,
                calibration = calibrationLine(
                    entity.a0,
                    entity.a1,
                    entity.a2,
                    entity.channelCount,
                ),
                algorithmVersions = algorithms,
                extra = extra,
                appVersion = appVersion,
            )
        }
    }
}
