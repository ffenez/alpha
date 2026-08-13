package app.radiacode.analysis

import app.radiacode.analysis.evidence.EvidenceClass
import app.radiacode.analysis.evidence.NuclideEvidence

/**
 * Семейства распада среди кандидатов: какие из них — родня по одному ряду.
 *
 * ## Зачем
 *
 * Таблица пиков перечисляет кандидатов подряд, и Pb-214 рядом с Bi-214
 * выглядит как две независимые находки. Это не так: они соседи по одному ряду
 * распада, их совместное присутствие ОЖИДАЕМО и само по себе — самое обычное
 * состояние природного фона. Человек, который этого не знает, читает список
 * как два разных вывода; названное родство снимает вопрос одной строкой.
 *
 * ## Чего здесь нет
 *
 * Ни вероятности, ни «обнаружен радон», ни оценки активности родителя.
 * Присутствие дочерних линий НЕ определяет родителя: доли ряда зависят от
 * равновесия, вентиляции и возраста материала, а этого мы не измеряем.
 * Утверждается ровно одно, проверяемое по библиотеке линий: эти нуклиды
 * принадлежат одному ряду.
 *
 * Чистая логика, JVM-тесты.
 */
object DecayFamilies {

    /**
     * @param chain имя ряда из [GammaLine.chain] — «Ra-226», «Th-232».
     * @param members нуклиды-кандидаты этого ряда, по возрастанию имени, чтобы
     *   строка не переставлялась от прогона к прогону.
     * @param radonProgeny ряд Ra-226, представленный именно дочерними
     *   продуктами радона (Pb-214, Bi-214) — у них есть собственное принятое
     *   имя, и называть их им честнее, чем номером ряда.
     */
    data class Family(
        val chain: String,
        val members: List<String>,
        val radonProgeny: Boolean,
    )

    /** Дочерние продукты радона-222 из ряда Ra-226 — те, что видит сцинтиллятор. */
    val RADON_PROGENY = setOf("Pb-214", "Bi-214")

    private const val RADON_CHAIN = "Ra-226"

    /**
     * Семейства среди кандидатов.
     *
     * Кандидат идёт в счёт, только если движок доказательств не отверг его
     * ([EvidenceClass.CONTRADICTED] не берётся): семейство из отвергнутых имён
     * было бы утверждением о том, чего на экране нет.
     *
     * Одинокий нуклид семейством не считается: родство — это утверждение о
     * ДВУХ и более, иначе строка сообщала бы лишь то, что у нуклида есть ряд.
     */
    fun of(candidates: List<NuclideEvidence>): List<Family> = candidates
        .filter { it.classification != EvidenceClass.CONTRADICTED }
        .mapNotNull { candidate -> candidate.chain?.let { it to candidate.nuclide } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, members) -> members.distinct().sorted() }
        .filterValues { it.size >= 2 }
        .map { (chain, members) ->
            Family(
                chain = chain,
                members = members,
                radonProgeny = chain == RADON_CHAIN && members.all { it in RADON_PROGENY },
            )
        }
        .sortedBy { it.chain }
}
