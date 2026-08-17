package app.alpha.data

import java.util.Locale

/**
 * Minimal flat JSON object codec for the parameter/statistics columns of
 * derived results (`experiments.params`, `experiment_runs.doseStats`,
 * `spectra.analysisMeta` — spec §22).
 *
 * Why not `org.json`: those columns are read and written by pure JVM code that
 * is unit-tested without Android, and the payloads are flat string→scalar maps.
 * A 60-line codec keeps the storage format explicit and the tests free of the
 * platform stub.
 *
 * Shape: `{"key":"value",...}`, every value stored as a string (numbers are
 * formatted with [Locale.ROOT] so a device locale can never turn `0.5` into
 * `0,5` on disk). Key order is preserved. Malformed input decodes to an empty
 * map instead of throwing — a stored analysis that cannot be read must not take
 * the whole screen down.
 */
object JsonMap {

    fun encode(values: Map<String, String>): String =
        values.entries.joinToString(",", prefix = "{", postfix = "}") { (key, value) ->
            "\"${escape(key)}\":\"${escape(value)}\""
        }

    /** Convenience: formats scalars deterministically, drops null values. */
    fun of(vararg pairs: Pair<String, Any?>): String {
        val map = LinkedHashMap<String, String>(pairs.size)
        for ((key, value) in pairs) {
            val text = format(value) ?: continue
            map[key] = text
        }
        return encode(map)
    }

    fun format(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        is Double -> String.format(Locale.ROOT, "%.6g", value)
        is Float -> String.format(Locale.ROOT, "%.6g", value.toDouble())
        else -> value.toString()
    }

    fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val text = raw.trim()
        if (!text.startsWith("{") || !text.endsWith("}")) return emptyMap()
        val result = LinkedHashMap<String, String>()
        var index = 1
        val end = text.length - 1
        while (index < end) {
            when (text[index]) {
                ' ', '\n', '\t', '\r', ',' -> {
                    index++
                    continue
                }
                '"' -> Unit
                else -> return result // unexpected token: keep what parsed
            }
            val key = readString(text, index) ?: return result
            index = key.second
            while (index < end && text[index].isWhitespace()) index++
            if (index >= end || text[index] != ':') return result
            index++
            while (index < end && text[index].isWhitespace()) index++
            if (index >= end || text[index] != '"') return result
            val value = readString(text, index) ?: return result
            index = value.second
            result[key.first] = value.first
        }
        return result
    }

    /** Reads a quoted string starting at [start]; returns value and next index. */
    private fun readString(text: String, start: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var i = start + 1
        while (i < text.length) {
            when (val ch = text[i]) {
                '\\' -> {
                    if (i + 1 >= text.length) return null
                    when (val escaped = text[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        else -> sb.append(escaped)
                    }
                    i += 2
                }
                '"' -> return sb.toString() to (i + 1)
                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        return null
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
