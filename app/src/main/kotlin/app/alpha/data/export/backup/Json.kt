package app.alpha.data.export.backup

/**
 * Минимальный JSON: запись потоком и разбор.
 *
 * ## Почему свой, а не библиотека
 *
 * В приложении нет ни одной JSON-библиотеки на основном исходнике, и добавлять
 * её ради формата резервной копии значило бы тащить зависимость (а с R8 — ещё
 * и правила сохранения) в проект, который сознательно держит их наперечёт.
 * Android-овский `org.json` не годится по другой причине: в обычных JVM-тестах
 * он заглушён, то есть формат копии нельзя было бы проверить без прибора и
 * эмулятора — а именно он должен проверяться в первую очередь.
 *
 * Здесь ровно столько JSON, сколько нужно формату: запись без промежуточного
 * дерева (копия пишется потоком, см. `BackupWriter`) и разбор, который на
 * испорченном файле честно бросает [JsonException], а не молча возвращает
 * пустоту. Молчаливое «ничего не разобралось» в восстановлении данных — худший
 * из возможных ответов.
 */
object Json {

    /**
     * Экранирование строки по RFC 8259.
     *
     * `<` и `>` экранируются тоже, хотя стандарт этого не требует: тот же
     * писатель кладёт JSON внутрь `<script>` в HTML-отчётах, и `</script>` в
     * заметке пользователя иначе закрыл бы тег.
     */
    fun escape(value: String): String {
        val out = StringBuilder(value.length + 16)
        for (ch in value) {
            when {
                ch == '"' -> out.append("\\\"")
                ch == '\\' -> out.append("\\\\")
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                ch == '<' -> out.append("\\u003c")
                ch == '>' -> out.append("\\u003e")
                ch == '&' -> out.append("\\u0026")
                ch < ' ' -> out.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    /** Строка в кавычках. */
    fun quote(value: String): String = "\"" + escape(value) + "\""

    /**
     * Число так, как его понимает JSON: без экспоненты локали и без «,».
     *
     * Не-конечные значения (NaN, ±∞) в JSON не существуют — вместо выдумки они
     * записываются как `null`, и читатель увидит отсутствие числа, а не ноль.
     */
    fun number(value: Double): String = when {
        !value.isFinite() -> "null"
        value == value.toLong().toDouble() && kotlin.math.abs(value) < 1e15 ->
            value.toLong().toString()
        else -> java.math.BigDecimal(value).round(java.math.MathContext(12)).toPlainString()
    }

    fun number(value: Float): String = number(value.toDouble())

    /** Пишущий поток: объекты, массивы и поля без промежуточного дерева. */
    class Writer(private val out: Appendable) {

        private var needComma = false

        fun beginObject(): Writer = apply {
            comma()
            out.append('{')
            needComma = false
        }

        fun endObject(): Writer = apply {
            out.append('}')
            needComma = true
        }

        fun beginArray(): Writer = apply {
            comma()
            out.append('[')
            needComma = false
        }

        fun endArray(): Writer = apply {
            out.append(']')
            needComma = true
        }

        fun name(name: String): Writer = apply {
            comma()
            out.append(quote(name)).append(':')
            needComma = false
        }

        fun value(value: String?): Writer = apply {
            comma()
            out.append(if (value == null) "null" else quote(value))
            needComma = true
        }

        fun value(value: Long?): Writer = apply {
            comma()
            out.append(value?.toString() ?: "null")
            needComma = true
        }

        fun value(value: Int?): Writer = value(value?.toLong())

        fun value(value: Double?): Writer = apply {
            comma()
            out.append(if (value == null) "null" else number(value))
            needComma = true
        }

        fun value(value: Float?): Writer = value(value?.toDouble())

        fun value(value: Boolean?): Writer = apply {
            comma()
            out.append(value?.toString() ?: "null")
            needComma = true
        }

        /** Поле «имя: значение» — самая частая пара, поэтому она одной строкой. */
        fun field(name: String, value: String?): Writer = name(name).value(value)
        fun field(name: String, value: Long?): Writer = name(name).value(value)
        fun field(name: String, value: Int?): Writer = name(name).value(value)
        fun field(name: String, value: Double?): Writer = name(name).value(value)
        fun field(name: String, value: Float?): Writer = name(name).value(value)
        fun field(name: String, value: Boolean?): Writer = name(name).value(value)

        private fun comma() {
            if (needComma) out.append(',')
            needComma = false
        }
    }

    // --- разбор -----------------------------------------------------------

    /** Разобранное значение. Числа хранятся текстом: точность решает читатель. */
    sealed interface Value {
        data object Null : Value
        data class Bool(val value: Boolean) : Value
        data class Num(val text: String) : Value
        data class Str(val value: String) : Value
        data class Arr(val items: List<Value>) : Value
        data class Obj(val fields: Map<String, Value>) : Value
    }

    fun parse(text: String): Value {
        val parser = Parser(text)
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) parser.fail("лишние символы после значения")
        return value
    }

    /** Объект верхнего уровня; на любом другом значении — честная ошибка. */
    fun parseObject(text: String): Value.Obj =
        parse(text) as? Value.Obj ?: throw JsonException("ожидался объект JSON")

    private class Parser(private val text: String) {

        private var index = 0

        fun atEnd(): Boolean = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun readValue(): Value {
            skipWhitespace()
            if (atEnd()) fail("пустое значение")
            return when (val ch = text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> Value.Str(readString())
                't' -> readLiteral("true", Value.Bool(true))
                'f' -> readLiteral("false", Value.Bool(false))
                'n' -> readLiteral("null", Value.Null)
                else -> if (ch == '-' || ch.isDigit()) readNumber() else fail("неожиданный символ «$ch»")
            }
        }

        private fun readObject(): Value {
            expect('{')
            val fields = LinkedHashMap<String, Value>()
            skipWhitespace()
            if (peek() == '}') {
                index++
                return Value.Obj(fields)
            }
            while (true) {
                skipWhitespace()
                val name = readString()
                skipWhitespace()
                expect(':')
                fields[name] = readValue()
                skipWhitespace()
                when (val ch = next()) {
                    ',' -> continue
                    '}' -> return Value.Obj(fields)
                    else -> fail("ожидались «,» или «}», встречено «$ch»")
                }
            }
        }

        private fun readArray(): Value {
            expect('[')
            val items = ArrayList<Value>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                return Value.Arr(items)
            }
            while (true) {
                items += readValue()
                skipWhitespace()
                when (val ch = next()) {
                    ',' -> continue
                    ']' -> return Value.Arr(items)
                    else -> fail("ожидались «,» или «]», встречено «$ch»")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (atEnd()) fail("строка не закрыта")
                when (val ch = text[index++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) fail("обрыв после «\\»")
                        when (val esc = text[index++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 > text.length) fail("обрыв в \\u")
                                val code = text.substring(index, index + 4).toIntOrNull(16)
                                    ?: fail("не шестнадцатеричный \\u")
                                index += 4
                                out.append(code.toChar())
                            }
                            else -> fail("неизвестная последовательность «\\$esc»")
                        }
                    }
                    else -> out.append(ch)
                }
            }
        }

        private fun readNumber(): Value {
            val start = index
            if (peek() == '-') index++
            while (!atEnd() && (text[index].isDigit() || text[index] in ".eE+-")) index++
            val slice = text.substring(start, index)
            if (slice.toDoubleOrNull() == null) fail("не число: «$slice»")
            return Value.Num(slice)
        }

        private fun readLiteral(literal: String, value: Value): Value {
            if (!text.startsWith(literal, index)) fail("ожидалось «$literal»")
            index += literal.length
            return value
        }

        private fun peek(): Char = if (atEnd()) ' ' else text[index]

        private fun next(): Char {
            if (atEnd()) fail("неожиданный конец")
            return text[index++]
        }

        private fun expect(ch: Char) {
            skipWhitespace()
            if (atEnd() || text[index] != ch) fail("ожидался «$ch»")
            index++
        }

        fun fail(message: String): Nothing =
            throw JsonException("$message (позиция $index)")
    }
}

/** Разбор не удался: файл не JSON или испорчен. */
class JsonException(message: String) : Exception(message)

// --- удобные чтения полей -------------------------------------------------

fun Json.Value.Obj.str(name: String): String? = (fields[name] as? Json.Value.Str)?.value

fun Json.Value.Obj.long(name: String): Long? =
    (fields[name] as? Json.Value.Num)?.text?.toDoubleOrNull()?.toLong()

fun Json.Value.Obj.int(name: String): Int? = long(name)?.toInt()

fun Json.Value.Obj.double(name: String): Double? =
    (fields[name] as? Json.Value.Num)?.text?.toDoubleOrNull()

fun Json.Value.Obj.float(name: String): Float? = double(name)?.toFloat()

fun Json.Value.Obj.bool(name: String): Boolean? = (fields[name] as? Json.Value.Bool)?.value

fun Json.Value.Obj.obj(name: String): Json.Value.Obj? = fields[name] as? Json.Value.Obj
