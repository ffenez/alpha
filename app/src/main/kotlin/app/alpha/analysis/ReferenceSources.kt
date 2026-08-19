package app.alpha.analysis

/**
 * Эталонные источники, по которым строится кривая эффективности.
 *
 * ## Почему отдельный список
 *
 * Для калибровки годится не любой нуклид из справочника, а только тот, у
 * которого есть паспорт: аттестованная активность на дату и период
 * полураспада, чтобы пересчитать её на день измерения. Здесь лежат ЧИСЛА —
 * период в секундах и квантовый выход долей, — а не строки для показа:
 * строковый период («30,08 года») в расчёт не подставить.
 *
 * Линии выбраны те, что уверенно разрешаются сцинтиллятором и не сливаются с
 * соседями своего же нуклида; слабые и близкие сознательно не берутся — на
 * них площадь пика не отделить от соседа, и точка кривой была бы завышена.
 *
 * Значения периодов и выходов — из оценённых данных (ENSDF/DDEP через
 * IAEA Live Chart of Nuclides и Laboratoire National Henri Becquerel,
 * рекомендованные данные для калибровочных источников).
 */
object ReferenceSources {

    /**
     * @property nuclide имя, как в остальном приложении
     * @property halfLifeSeconds период полураспада, с
     * @property lines линии, годные для калибровки
     */
    data class Source(
        val nuclide: String,
        val halfLifeSeconds: Double,
        val lines: List<Line>,
    )

    /**
     * @property energyKeV энергия линии, кэВ
     * @property intensity квантовый выход — доля распадов, дающих этот квант
     * @property intensitySigma неопределённость выхода той же долей
     */
    data class Line(
        val energyKeV: Double,
        val intensity: Double,
        val intensitySigma: Double,
    )

    private fun years(value: Double) = value * ActivityMath.SECONDS_PER_YEAR
    private fun days(value: Double) = value * 24 * 3600

    val ALL: List<Source> = listOf(
        // Cs-137: 30,08 года; линия 661,657 кэВ, выход 85,10(20) %.
        Source(
            nuclide = "Cs-137",
            halfLifeSeconds = years(30.08),
            lines = listOf(Line(661.657, 0.8510, 0.0020)),
        ),
        // Co-60: 5,2711 года; две линии почти со стопроцентным выходом.
        Source(
            nuclide = "Co-60",
            halfLifeSeconds = years(5.2711),
            lines = listOf(
                Line(1173.228, 0.9985, 0.0003),
                Line(1332.492, 0.999826, 0.000006),
            ),
        ),
        // Am-241: 432,6 года; 59,54 кэВ, выход 35,92(17) %.
        Source(
            nuclide = "Am-241",
            halfLifeSeconds = years(432.6),
            lines = listOf(Line(59.5409, 0.3592, 0.0017)),
        ),
        // Ba-133: 10,551 года. Линии 81 и 356 кэВ разрешаются; 302,9 и 356,0
        // на сцинтилляторе сливаются, поэтому 302,9 не берётся.
        Source(
            nuclide = "Ba-133",
            halfLifeSeconds = years(10.551),
            lines = listOf(
                Line(80.9979, 0.3306, 0.0022),
                Line(356.0129, 0.6205, 0.0019),
            ),
        ),
        // Na-22: 2,6018 года; 511 кэВ — аннигиляционная (выход 180 %), и она
        // НЕ берётся: часть аннигиляций происходит вне источника, и её выход
        // зависит от окружения, а не только от нуклида.
        Source(
            nuclide = "Na-22",
            halfLifeSeconds = years(2.6018),
            lines = listOf(Line(1274.537, 0.9994, 0.0013)),
        ),
        // Eu-152: 13,517 года; набор линий по всему диапазону — самый удобный
        // одиночный эталон для кривой. Взяты разрешаемые сцинтиллятором.
        Source(
            nuclide = "Eu-152",
            halfLifeSeconds = years(13.517),
            lines = listOf(
                Line(121.7817, 0.2853, 0.0016),
                Line(344.2785, 0.2659, 0.0012),
                Line(778.9045, 0.1293, 0.0008),
                Line(1408.013, 0.2087, 0.0009),
            ),
        ),
        // Co-57: 271,74 суток; 122,06 кэВ, выход 85,60(17) %. Линия 136,5
        // отстоит на 14 кэВ и сцинтиллятором не отделяется.
        Source(
            nuclide = "Co-57",
            halfLifeSeconds = days(271.74),
            lines = listOf(Line(122.0614, 0.8560, 0.0017)),
        ),
        // Mn-54: 312,20 суток; 834,848 кэВ, выход 99,976 %.
        Source(
            nuclide = "Mn-54",
            halfLifeSeconds = days(312.20),
            lines = listOf(Line(834.848, 0.99976, 0.00001)),
        ),
    )

    fun of(nuclide: String): Source? = ALL.firstOrNull { it.nuclide == nuclide }
}
