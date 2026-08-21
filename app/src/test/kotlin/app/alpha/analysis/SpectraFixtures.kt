package app.alpha.analysis

/**
 * Настоящие спектры прибора для тестов: фон (71 ч) и источник Th-232 (7,7 ч).
 *
 * Калибровка восстанавливается по ВСЕЙ колонке энергий методом наименьших
 * квадратов. По трём соседним точкам её брать нельзя: энергии в файле округлены
 * до сотых, и вторая разность (из которой берётся a2) тонет в округлении —
 * восстановленная шкала уходила в минус на верхнем краю.
 */
object SpectraFixtures {

    fun load(name: String): Pair<List<Int>, EnergyCalibration> {
        val lines = javaClass.classLoader!!
            .getResourceAsStream("spectra/$name")!!
            .bufferedReader().use { it.readLines() }
            .drop(1)
            .filter { it.isNotBlank() }
        val counts = lines.map { it.split(",")[2].trim().toInt() }
        val energies = lines.map { it.split(",")[1].trim().toDouble() }
        return counts to fitCalibration(energies)
    }

    /** Квадратичная шкала по точкам «канал → энергия», нормальные уравнения. */
    fun fitCalibration(energies: List<Double>): EnergyCalibration {
        val n = energies.size
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        var s3 = 0.0
        var s4 = 0.0
        var t0 = 0.0
        var t1 = 0.0
        var t2 = 0.0
        for (i in 0 until n) {
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
