package app.alpha.analysis

import java.io.File

/**
 * Настоящие спектры прибора — для проверок, которые запускаются ТОЛЬКО у
 * владельца прибора.
 *
 * ## Зачем отдельный источник
 *
 * Измерения человека в репозиторий не попадают: репозиторий публичный, а его
 * данные — его дело. Но именно на настоящих формах видно то, чего не выдумать:
 * несимметричные хвосты линий, наклонный континуум, реальный ход шкалы.
 * Поэтому такие спектры лежат в `local/spectra/` — каталоге, который
 * `.gitignore` не пускает в git, — и проверки по ним запускаются, когда файлы
 * там есть.
 *
 * ## Почему пропуск, а не падение
 *
 * У всех, кроме владельца прибора, каталог пуст, и падающий тест означал бы
 * «сломано», хотя сломанного ничего нет. Пропуск при этом ВИДЕН: тест
 * печатает причину, а не молча зеленеет. Обязательные проверки движка живут
 * на построенных спектрах ([SyntheticSpectra]) и идут всегда.
 */
object LocalSpectra {

    /** Каталог с данными прибора; в git его нет и не будет. */
    private val directory: File = File("local/spectra")

    /** Файл, если он положен; null — проверку надо пропустить. */
    fun file(name: String): File? = directory.resolve(name).takeIf { it.isFile }

    /**
     * Спектр из CSV «канал, энергия, счёт» — формат выгрузки приложения.
     *
     * @return счёт по каналам и калибровка, восстановленная по колонке энергий
     *   методом наименьших квадратов; null — файла нет.
     */
    fun csv(name: String): Pair<List<Int>, EnergyCalibration>? {
        val source = file(name) ?: return null
        val rows = source.readLines().drop(1).filter { it.isNotBlank() }
        val counts = rows.map { it.split(",")[2].trim().toInt() }
        val energies = rows.map { it.split(",")[1].trim().toDouble() }
        return counts to fitCalibration(energies)
    }

    /** Есть ли хоть что-то для локальных проверок. */
    fun available(): Boolean = directory.isDirectory && (directory.listFiles()?.isNotEmpty() == true)

    /**
     * Квадратичная шкала по точкам «канал → энергия», нормальные уравнения.
     *
     * По трём соседним точкам её брать нельзя: энергии в файле округлены до
     * сотых, и вторая разность целиком тонет в округлении.
     */
    private fun fitCalibration(energies: List<Double>): EnergyCalibration {
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        var s3 = 0.0
        var s4 = 0.0
        var t0 = 0.0
        var t1 = 0.0
        var t2 = 0.0
        for (i in energies.indices) {
            val x = i.toDouble()
            val x2 = x * x
            s0 += 1.0
            s1 += x
            s2 += x2
            s3 += x2 * x
            s4 += x2 * x2
            t0 += energies[i]
            t1 += energies[i] * x
            t2 += energies[i] * x2
        }
        val m = arrayOf(
            doubleArrayOf(s0, s1, s2, t0),
            doubleArrayOf(s1, s2, s3, t1),
            doubleArrayOf(s2, s3, s4, t2),
        )
        for (col in 0 until 3) {
            var pivot = col
            for (row in col until 3) {
                if (kotlin.math.abs(m[row][col]) > kotlin.math.abs(m[pivot][col])) pivot = row
            }
            val tmp = m[col]
            m[col] = m[pivot]
            m[pivot] = tmp
            val head = m[col][col]
            for (k in col..3) m[col][k] /= head
            for (row in 0 until 3) {
                if (row == col) continue
                val factor = m[row][col]
                for (k in col..3) m[row][k] -= factor * m[col][k]
            }
        }
        return EnergyCalibration(m[0][3].toFloat(), m[1][3].toFloat(), m[2][3].toFloat())
    }
}
