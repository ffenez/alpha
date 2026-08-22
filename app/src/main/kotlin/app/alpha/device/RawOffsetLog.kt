package app.alpha.device

import app.alpha.protocol.DataBufRecord
import app.alpha.protocol.RealTimeData

/**
 * Журнал СЫРЫХ смещений записей прибора — диагностика на один вопрос.
 *
 * ## Зачем
 *
 * Прибор хранит автономные наблюдения и отдаёт их после подключения тем же
 * `DATA_BUF`, что и живые данные. Метка записи считается от эмпирической базы
 * «подключение + 128 с» — константы, снятой сообществом на 10x и заведомо
 * неверной на других моделях: на Zero записи уезжают на минуты. Пока неизвестно,
 * какова эта база НА ЭТОМ приборе и насколько глубок его буфер, чинить привязку
 * времени нечем: любое исправление будет подгонкой под догадку.
 *
 * Журнал отвечает ровно на это: он пишет смещения так, как их прислал прибор,
 * не применяя никаких поправок. По самому старому смещению видно глубину
 * буфера, по тому, за сколько ответов новейшая запись догоняет настоящее
 * время, — как идёт слив.
 *
 * ## Почему в памяти, а не в базе
 *
 * Это разовое измерение прибора, а не данные наблюдения: оно живёт, пока
 * включено, и уходит в файл по кнопке. Хранить его между запусками значило бы
 * заводить таблицу ради эксперимента.
 *
 * ## Границы
 *
 * Журнал ничего не исправляет и ни на что не влияет. Единственный писатель —
 * [DeviceConnection]; выключенный журнал не стоит ничего, кроме проверки флага.
 */
object RawOffsetLog {

    /**
     * Сколько строк держится в памяти — **инженерный параметр**. Прибор
     * опрашивается раз в секунду, две тысячи строк это около получаса
     * наблюдения: слив буфера у известных реализаций занимает единицы минут,
     * то есть запас на порядок.
     */
    const val CAPACITY = 2_000

    @Volatile
    var enabled: Boolean = false

    private val lines = ArrayDeque<String>()

    val size: Int get() = synchronized(lines) { lines.size }

    /** Заголовок сессии: без базы и серийника смещения не истолковать. */
    fun session(nowMillis: Long, baseTimeMillis: Long, serial: String?) {
        if (!enabled) return
        add(
            "session at=$nowMillis base=$baseTimeMillis base_offset_ms=${baseTimeMillis - nowMillis}" +
                " serial=${serial ?: "-"}",
        )
    }

    /**
     * Одна строка на ответ прибора.
     *
     * @param records записи ответа, как их отдал декодер.
     * @param correctionMillis поправка, ДЕЙСТВОВАВШАЯ при разборе этого ответа.
     * @param baseTimeMillis эмпирическая база сессии.
     */
    fun reply(
        nowMillis: Long,
        records: List<DataBufRecord>,
        correctionMillis: Long,
        baseTimeMillis: Long,
    ) {
        if (!enabled) return
        if (records.isEmpty()) {
            add("reply at=$nowMillis records=0 corr=$correctionMillis")
            return
        }
        val offsets = records.map { it.tsOffset10ms }
        val realTime = records.filterIsInstance<RealTimeData>()
        val newestRealTime = realTime.maxByOrNull { it.tsOffset10ms }?.tsOffset10ms
        // Возраст новейшей записи: сколько времени назад её сделал прибор, если
        // верить нынешней базе. Ноль означает «слив догнал живое».
        val ageSeconds = newestRealTime?.let {
            (nowMillis - (baseTimeMillis + correctionMillis + it.toLong() * 10L)) / 1000.0
        }
        add(
            "reply at=$nowMillis records=${records.size} rt=${realTime.size}" +
                " raw_min=${offsets.min()} raw_max=${offsets.max()}" +
                " raw_newest_rt=${newestRealTime ?: "-"}" +
                " age_s=${ageSeconds?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "-"}" +
                " corr=$correctionMillis",
        )
    }

    /** Журнал текстом; пустая строка — записей нет. */
    fun dump(): String = synchronized(lines) { lines.joinToString("\n") }

    fun clear() = synchronized(lines) { lines.clear() }

    private fun add(line: String) {
        synchronized(lines) {
            // Кольцо: диагностика не имеет права съесть память процесса.
            if (lines.size >= CAPACITY) lines.removeFirst()
            lines.addLast(line)
        }
    }
}
