package app.alpha.ui.text

/**
 * Офлайн-справка о нуклиде, открываемая тапом по строке таблицы пиков.
 *
 * Карточка описывает НУКЛИД, а не находку: приложение нашло пик, энергия
 * которого совместима с линией библиотеки (спец §12). Поэтому в английском,
 * как и в русском, нет ни «detected», ни советов по безопасности, ни доз —
 * «возможное совпадение» переводится ровно как «possible match», без усиления.
 *
 * Что НЕ переводится: символы нуклидов (Cs-137), энергии линий, выходы и
 * числа периодов полураспада — это данные, а не текст. Десятичная запятая
 * тоже сохраняется в обоих языках: числа карточки печатает общий для
 * приложения форматтер, и разный разделитель в одной карточке читался бы как
 * опечатка.
 */
interface NuclideStrings {

    // ------------------------------------------------------ статусный блок
    // Карточка открывается не абзацем, а статусом: человек пришёл из строки
    // кандидата и первым делом спрашивает «что именно совпало».
    val statusPossibleMatch: String
    val statusNotConfirmed: String
    val statusNotEvaluated: String
    val statusNotEvaluatedDetail: String

    /** Все совпавшие линии кандидата прибор не отличает от чужих. */
    val statusAmbiguous: String

    /** «Pb-214 / I-131: …» — группа неразрешимости, победитель не назначается. */
    fun ambiguousDetail(group: String): String

    /** Противоречие без конкретной пропавшей линии (разошлись отношения). */
    val contradictsExpectedLines: String

    /** «Совпала 1 из 3 проверяемых линий.» — счёт берётся у matcher. */
    fun matchedOfChecked(found: Int, total: Int): String
    val notEnoughToConfirm: String

    /**
     * У нуклида ровно одна гамма-линия.
     *
     * «Совпала 1 из 1 проверяемых линий. Этого недостаточно» читается как
     * противоречие: совпало всё, что было, — и всё равно мало. Причина не в
     * счёте, а в том, что второй линии у нуклида нет и перекрёстно проверить
     * совпадение нечем.
     */
    fun singleLineNuclide(nuclide: String): String

    /** Линии есть, но проверить на этом спектре удалось только одну. */
    fun onlyOneLineCheckable(nuclide: String): String
    val multiLineStronger: String

    /** «Совпал пик около 1120 кэВ, но линии 609 кэВ в спектре нет.» */
    fun missingStrongLine(matched: String, missing: String): String

    /** Одна оговорка про ограничение метода — внизу карточки и только там. */
    val limits: String

    // --------------------------------------------------- подписи и разделы
    val labelOrigin: String
    val labelHalfLife: String
    val labelDecay: String
    val sectionAbout: String
    val sectionEveryday: String
    val sectionConfirmation: String
    val sectionLimitation: String
    val allLinesLabel: String
    val close: String

    val originNatural: String
    val originArtificial: String

    /** «природный · ряд Th-232» — родитель цепочки дописывается к происхождению. */
    fun originWithChain(origin: String, chain: String): String

    /** «609,3 кэВ · 45,5 % на распад»: числа приходят уже отформатированными. */
    fun gammaLine(energy: String, intensity: String): String

    // -------------------------------------------------- проверка по линиям
    val sectionLineCheck: String
    val unitKeV: String

    /** Шапка таблицы: линия · выход · результат. */
    val columnLine: String
    val columnYield: String
    val columnResult: String

    /** Что такое «выход»: проценты линий не обязаны давать в сумме 100 %. */
    val yieldNote: String

    fun lineYield(percent: String): String
    val lineMatched: String
    val lineNotFound: String
    val lineTooWeak: String

    /** Спектр не сопоставлялся — это не «линии нет». */
    val lineNotEvaluated: String

    /** Не найдена, а ждать ли её видимой — оценить нечем (нет опоры/континуума). */
    val lineUndetermined: String

    /** Энергия линии вне шкалы прибора — вопрос о видимости не ставится. */
    val lineOutOfScale: String

    /** «пик 1109,3 кэВ · ΔE −11,0 кэВ» — только если matcher знает пик. */
    fun peakDelta(measured: String, delta: String): String

    /** Строка таблицы нажимается: подсказка стоит один раз под таблицей. */
    val lineShowHint: String

    /** Что делает нажатие строки — для экранного диктора. */
    fun lineShowAction(energy: String): String

    // ------------------------------------------- что усилило бы гипотезу
    fun bulletOtherLines(lines: String): String
    fun bulletChainLines(lines: String): String
    val bulletHoldsUp: String
    val bulletNoContradiction: String
    val bulletMoreStatistics: String

    // ------------------------------------------------ источник данных линий
    /** «Данные линий: ENSDF …» — одна строка вместо библиографии у каждой. */
    fun lineDataSource(sources: String): String
    val sourceEnsdf: String
    val sourceDdep: String

    fun netAreaRatio(fromKeV: String, toKeV: String, value: String, sigma: String): String
    fun expectedByYield(value: String): String

    /**
     * Почему табличное отношение нельзя сравнивать напрямую. Русский текст
     * повторяет `DetectorEfficiency.UNAVAILABLE_NOTE`: сама константа живёт в
     * `analysis` и запинена своим тестом, а карточке нужен ПЕРЕВОДИМЫЙ текст.
     */
    val efficiencyNotCalibrated: String

    // ----------------------------------------------------------- нуклиды
    // Символ («K-40»), энергии линий и выходы — данные, они лежат в
    // NuclideInfoLibrary и не переводятся; здесь только текст.

    val ac228Name: String
    val ac228HalfLife: String
    val ac228Decay: String
    val ac228Everyday: String
    val ac228Confirmation: String

    val bi212Name: String
    val bi212HalfLife: String
    val bi212Decay: String
    val bi212Everyday: String
    val bi212Confirmation: String

    val ra226Name: String
    val ra226HalfLife: String
    val ra226Decay: String
    val ra226Everyday: String
    val ra226Confirmation: String

    val u235Name: String
    val u235HalfLife: String
    val u235Decay: String
    val u235Everyday: String
    val u235Confirmation: String

    val la138Name: String
    val la138HalfLife: String
    val la138Decay: String
    val la138Everyday: String
    val la138Confirmation: String

    val cs134Name: String
    val cs134HalfLife: String
    val cs134Decay: String
    val cs134Everyday: String
    val cs134Confirmation: String

    val k40Name: String
    val k40HalfLife: String
    val k40Decay: String
    val k40Everyday: String
    val k40Confirmation: String

    val cs137Name: String
    val cs137HalfLife: String
    val cs137Decay: String
    val cs137Everyday: String
    val cs137Confirmation: String

    val co60Name: String
    val co60HalfLife: String
    val co60Decay: String
    val co60Everyday: String
    val co60Confirmation: String

    val i131Name: String
    val i131HalfLife: String
    val i131Decay: String
    val i131Everyday: String
    val i131Confirmation: String

    val am241Name: String
    val am241HalfLife: String
    val am241Decay: String
    val am241Everyday: String
    val am241Confirmation: String

    val bi214Name: String
    val bi214HalfLife: String
    val bi214Decay: String
    val bi214Everyday: String
    val bi214Confirmation: String

    val pb214Name: String
    val pb214HalfLife: String
    val pb214Decay: String
    val pb214Everyday: String
    val pb214Confirmation: String

    val pb212Name: String
    val pb212HalfLife: String
    val pb212Decay: String
    val pb212Everyday: String
    val pb212Confirmation: String

    val tl208Name: String
    val tl208HalfLife: String
    val tl208Decay: String
    val tl208Everyday: String
    val tl208Confirmation: String
}

object NuclideRu : NuclideStrings {

    override val statusPossibleMatch = "ВОЗМОЖНОЕ СОВПАДЕНИЕ"
    override val statusNotConfirmed = "СОВПАДЕНИЕ НЕ ПОДТВЕРЖДАЕТСЯ"
    override val statusNotEvaluated = "СПЕКТР НЕ СОПОСТАВЛЯЛСЯ"
    override val statusNotEvaluatedDetail =
        "Карточка открыта как справочник: показаны библиотечные данные нуклида, " +
            "проверки по линиям не было."

    override val statusAmbiguous = "ПРИБОР ЛИНИИ НЕ РАЗДЕЛЯЕТ"

    override fun ambiguousDetail(group: String) =
        "$group: совпавшие линии лежат ближе, чем разрешение прибора. " +
            "Прибор эти линии не разделяет, поэтому победитель не назначается."

    override val contradictsExpectedLines =
        "Наблюдаемое противоречит ожидаемым линиям этого нуклида."

    // Русский счёт: «Совпала 1», «Совпали 2–4», «Совпало 0 и 5+». Знаменатель
    // называется всегда — доля без него читается как вердикт.
    override fun matchedOfChecked(found: Int, total: Int): String {
        val verb = when {
            found % 10 == 1 && found % 100 != 11 -> "Совпала"
            found % 10 in 2..4 && found % 100 !in 12..14 -> "Совпали"
            else -> "Совпало"
        }
        return "$verb $found из $total проверяемых линий."
    }

    override val notEnoughToConfirm = "Этого недостаточно для подтверждения."

    override fun singleLineNuclide(nuclide: String) =
        "У $nuclide одна гамма-линия — перекрёстно проверить совпадение нечем: " +
            "у любого совпадения по одной энергии остаётся эта неопределённость."

    override fun onlyOneLineCheckable(nuclide: String) =
        "Проверить удалось только одну линию $nuclide: об остальных на этом спектре судить " +
            "нечем — они вне шкалы прибора или слишком слабы, чтобы их различить."
    override val multiLineStronger =
        "Несколько линий согласуются между собой — это сильнее одной линии, " +
            "но подтверждением не является."

    override fun missingStrongLine(matched: String, missing: String) =
        "Совпал пик около $matched кэВ, но более интенсивная линия $missing кэВ " +
            "в спектре не выделена."

    override val limits =
        "Совпадение энергии пика не идентифицирует нуклид само по себе. Близкие " +
            "линии могут быть неразличимы при энергетическом разрешении " +
            "детектора; результат зависит от статистики и калибровки."

    override val labelOrigin = "происхождение"
    override val labelHalfLife = "T½"
    override val labelDecay = "распад"
    override val sectionAbout = "О нуклиде"
    override val sectionEveryday = "Где встречается"
    override val sectionConfirmation = "Что усилило бы гипотезу"
    override val sectionLimitation = "Ограничение"
    override val allLinesLabel = "Все гамма-линии"
    override val close = "Закрыть"

    override val originNatural = "природный"
    override val originArtificial = "искусственный"

    override fun originWithChain(origin: String, chain: String) = "$origin · ряд $chain"

    override fun gammaLine(energy: String, intensity: String) =
        "$energy кэВ · $intensity % на распад"

    override val sectionLineCheck = "Проверка по линиям"
    override val unitKeV = "кэВ"

    override val columnLine = "линия"
    override val columnYield = "выход"
    override val columnResult = "результат"

    override val yieldNote =
        "Выход — вероятность гамма-эмиссии на распад этого нуклида; проценты " +
            "разных линий не обязаны в сумме давать 100 %."

    override fun lineYield(percent: String) = "$percent %"
    override val lineMatched = "совпала"
    override val lineNotFound = "не найдена"
    override val lineTooWeak = "слабая, различить нельзя"
    override val lineNotEvaluated = "не проверялась"
    override val lineUndetermined = "не найдена; ждать ли её — оценить нельзя"
    override val lineOutOfScale = "вне шкалы прибора"

    override fun peakDelta(measured: String, delta: String) =
        "пик $measured кэВ · ΔE $delta кэВ"

    override val lineShowHint = "Нажатие строки показывает, где эта линия на спектре."
    override fun lineShowAction(energy: String) = "Показать $energy кэВ на спектре"

    override fun bulletOtherLines(lines: String) =
        "совпадение других линий этого нуклида: $lines"

    override fun bulletChainLines(lines: String) =
        "совместимые линии того же ряда: $lines"

    override val bulletHoldsUp =
        "устойчивость совпавших линий при более долгом накоплении"

    override val bulletNoContradiction =
        "отсутствие противоречия с более интенсивными линиями нуклида"

    override val bulletMoreStatistics =
        "больше статистики в этих энергетических областях — дольше накопление"

    override fun lineDataSource(sources: String) = "Данные линий: $sources"



    override val sourceEnsdf = "ENSDF (IAEA Live Chart / NNDC NuDat 3)"
    override val sourceDdep = "DDEP/LNHB"


    override fun netAreaRatio(fromKeV: String, toKeV: String, value: String, sigma: String) =
        "Отношение нетто-площадей $fromKeV/$toKeV кэВ: $value ± $sigma"

    override fun expectedByYield(value: String) =
        "По табличным выходам: $value — без поправки на эффективность детектора"

    override val efficiencyNotCalibrated =
        "Точное сравнение отношения линий ограничено: относительная эффективность " +
            "детектора для этих энергий не откалибрована, поэтому наблюдаемое и " +
            "табличное отношения показаны раздельно."


    override val ac228Name = "актиний-228"
    override val ac228HalfLife = "6,15 ч"
    override val ac228Decay = "β⁻ → Th-228"
    override val ac228Everyday =
        "Дочерний в ряду тория-232, поэтому встречается там же, где сам торий: " +
            "гранит, монацитовый песок, калильные сетки, старая оптика. Его линии " +
            "911 и 969 кэВ — самые заметные в природном фоне после 1461 кэВ калия."
    override val ac228Confirmation =
        "Осмысленное совпадение — 911 и 969 кэВ вместе, лучше с 338 кэВ и с " +
            "линиями других членов ряда (238,6 кэВ Pb-212, 2615 кэВ Tl-208). " +
            "Одна линия 911 кэВ стоит рядом с 934 кэВ Bi-214 из уранового ряда."

    override val bi212Name = "висмут-212"
    override val bi212HalfLife = "60,6 мин"
    override val bi212Decay = "β⁻ (64 %) → Po-212, α (36 %) → Tl-208"
    override val bi212Everyday =
        "Ещё один член ториевого ряда; в спектре сцинтиллятора у него различима " +
            "одна линия 727 кэВ, остальные слабы или закрыты соседями."
    override val bi212Confirmation =
        "Сама по себе линия 727 кэВ значит мало: подтверждает её присутствие " +
            "остальной ряд — 238,6 кэВ Pb-212, 583 и 2615 кэВ Tl-208, 911 кэВ Ac-228."

    override val ra226Name = "радий-226"
    override val ra226HalfLife = "1600 лет"
    override val ra226Decay = "α → Rn-222"
    override val ra226Everyday =
        "Начало уранового ряда в его равновесной части: строительный камень, " +
            "фосфогипс, старые светящиеся циферблаты. В воздухе его продолжение — " +
            "радон и его дочерние продукты."
    override val ra226Confirmation =
        "Собственная линия 186 кэВ слаба и неотличима от 185,7 кэВ урана-235: " +
            "по ней одной выбрать нельзя. О присутствии ряда говорят линии дочерних " +
            "продуктов — 352 кэВ Pb-214 и 609 кэВ Bi-214."

    override val u235Name = "уран-235"
    override val u235HalfLife = "7,04·10⁸ лет"
    override val u235Decay = "α → Th-231"
    override val u235Everyday =
        "0,72 % природного урана: урановая руда, некоторые рудные образцы и " +
            "минералы. В обычной комнате его линий не бывает."
    override val u235Confirmation =
        "Главная линия 185,7 кэВ совпадает с 186,2 кэВ радия-226, и прибор их не " +
            "разделяет. Различие даёт набор: 143,8 и 205,3 кэВ есть только у урана, " +
            "а линии радонового ряда — только у радия."

    override val la138Name = "лантан-138"
    override val la138HalfLife = "1,02·10¹¹ лет"
    override val la138Decay = "захват электрона (66 %) → Ba-138, β⁻ (34 %) → Ce-138"
    override val la138Everyday =
        "0,09 % природного лантана: редкоземельные минералы (монацит, бастнезит), " +
            "полирующие порошки, некоторые лампы и катализаторы. Собственное " +
            "излучение сцинтилляторов на основе лантана к нашему прибору не " +
            "относится — у него другой кристалл."
    override val la138Confirmation =
        "Осмысленное совпадение — 1436 и 789 кэВ вместе в отношении примерно 2:1. " +
            "Линия 1436 кэВ стоит недалеко от 1461 кэВ калия-40, и на этом приборе " +
            "они разделяются только при долгом накоплении."

    override val cs134Name = "цезий-134"
    override val cs134HalfLife = "2,06 года"
    override val cs134Decay = "β⁻ → Ba-134"
    override val cs134Everyday =
        "Искусственный: появляется вместе с цезием-137 в свежих выбросах и в " +
            "образцах из зон загрязнения. Из-за короткого периода его отношение к " +
            "цезию-137 говорит о возрасте выброса."
    override val cs134Confirmation =
        "Осмысленное совпадение — 605 и 796 кэВ вместе, они почти равны по выходу. " +
            "Линия 605 кэВ соседствует с 609 кэВ Bi-214 из природного ряда, поэтому " +
            "без второй линии вывода нет."

    override val k40Name = "калий-40"
    override val k40HalfLife = "1,248·10⁹ лет"
    override val k40Decay =
        "β⁻ (89,3 %) → Ca-40; захват электрона (10,7 %) → Ar-40, " +
            "фотон 1460,8 кэВ рождается именно в этой ветви"
    override val k40Everyday =
        "Постоянная примесь природного калия — 0,0117 % его атомов, " +
            "доля не зависит от происхождения калия. Поэтому линия 1461 кэВ " +
            "видна от заменителей соли и удобрений на хлориде калия, бананов " +
            "и других богатых калием продуктов, гранита, бетона, золы и от " +
            "тела самого человека."
    override val k40Confirmation =
        "Других линий у K-40 в диапазоне прибора нет, так что " +
            "подтвердить совпадение второй линией нельзя. Косвенный довод — " +
            "устойчивость пика при долгом накоплении и его рост рядом с " +
            "калийным материалом."

    override val cs137Name = "цезий-137"
    override val cs137HalfLife = "30,08 года"
    override val cs137Decay =
        "β⁻ → Ba-137m (94,7 %), метастабильный барий за 2,55 мин " +
            "переходит в основное состояние и излучает 661,7 кэВ"
    override val cs137Everyday =
        "Продукт деления: глобальные следы атмосферных испытаний " +
            "и аварий в верхнем слое почвы, в лесных грибах и дичи " +
            "загрязнённых районов. Как закрытый источник встречается в " +
            "уровнемерах, плотномерах и калибровочных наборах."
    override val cs137Confirmation =
        "Единственная заметная линия, поэтому одиночного пика " +
            "мало. Осмысленный довод — сравнение с записанным опорным фоном " +
            "того же места и повтор на другом накоплении."

    override val co60Name = "кобальт-60"
    override val co60HalfLife = "5,27 года"
    override val co60Decay =
        "β⁻ → Ni-60; обе линии испускаются каскадом почти при каждом распаде"
    override val co60Everyday =
        "Промышленная радиография и стерилизация, медицинские " +
            "телетерапевтические установки, калибровочные источники; изредка " +
            "попадает в переплавленный металлолом."
    override val co60Confirmation =
        "Линии 1173 и 1333 кэВ рождаются каскадом, поэтому " +
            "осмысленное совпадение требует ОБЕ, причём примерно равной " +
            "площади. Одинокий бугор около 1173 кэВ — не Co-60."

    override val i131Name = "йод-131"
    override val i131HalfLife = "8,03 суток"
    override val i131Decay = "β⁻ → Xe-131"
    override val i131Everyday =
        "Медицинский изотоп: диагностика и лечение щитовидной " +
            "железы. Человек, недавно прошедший процедуру, остаётся " +
            "источником несколько дней — это самая частая бытовая встреча " +
            "с ним, в том числе в транспорте."
    override val i131Confirmation =
        "Вторая линия 637 кэВ слабая, но её отсутствие при сильной 364 кэВ — " +
            "довод против. Восьмидневный период полураспада проверяем только " +
            "СЕРИЕЙ сопоставимых измерений: одно накопление о спаде не говорит " +
            "ничего."

    override val am241Name = "америций-241"
    override val am241HalfLife = "432,6 года"
    override val am241Decay = "α → Np-237, сопровождается фотоном 59,5 кэВ"
    override val am241Everyday =
        "Ионизационные датчики дыма — самый распространённый " +
            "бытовой источник; также промышленные толщиномеры и плотномеры."
    override val am241Confirmation =
        "59,5 кэВ лежит там, где отклик CsI(Tl) и заводская " +
            "энергетическая калибровка наименее точны, поэтому " +
            "низкоэнергетическое совпадение заслуживает особого сомнения. " +
            "Других линий в диапазоне прибора нет."

    override val bi214Name = "висмут-214"
    override val bi214HalfLife = "19,9 минуты"
    // Происхождение уже сказано строкой «природный · ряд Ra-226» — в распаде
    // оно не повторяется, иначе соседние строки говорят одно и то же дважды.
    override val bi214Decay = "β⁻ → Po-214"
    override val bi214Everyday =
        "Короткоживущий продукт цепочки радона-222. Его гамма-линии могут " +
            "наблюдаться в естественном фоне — прежде всего там, где в воздухе " +
            "есть продукты распада радона: подвалы, погреба, нижние этажи."
    override val bi214Confirmation =
        "Радоновая цепочка узнаётся по НЕСКОЛЬКИМ линиям сразу — 609, 1120 и " +
            "1765 кэВ вместе с 352 кэВ от Pb-214. Спад со временем что-то " +
            "значит только в серии сопоставимых измерений при известных " +
            "условиях, по одному накоплению его не проверить."

    override val pb214Name = "свинец-214"
    override val pb214HalfLife = "26,9 минуты"
    override val pb214Decay = "β⁻ → Bi-214"
    override val pb214Everyday =
        "Тот же радоновый ряд, что и Bi-214: подвалы, погреба, " +
            "гранит и туф, воздух после дождя."
    override val pb214Confirmation =
        "Идёт в паре с Bi-214, поэтому 352 кэВ без 609 кэВ выглядит странно. " +
            "Обе линии вместе — гораздо более осмысленный довод, чем один пик; " +
            "спад со временем читается только по серии измерений."

    override val pb212Name = "свинец-212"
    override val pb212HalfLife = "10,64 часа"
    override val pb212Decay = "β⁻ → Bi-212"
    override val pb212Everyday =
        "Ториевый ряд, ветвь торона (Rn-220): старые калильные сетки газовых и " +
            "керосиновых ламп, ториевое оптическое стекло старых " +
            "объективов, монацитовый песок, некоторые сварочные электроды, " +
            "а также обычный гранит."
    override val pb212Confirmation =
        "Ториевая цепочка узнаётся по 238,6 кэВ ВМЕСТЕ с " +
            "583 и 2615 кэВ от Tl-208. Линия 2615 кэВ стоит особняком в " +
            "спектре и потому самая показательная."

    override val tl208Name = "таллий-208"
    override val tl208HalfLife = "3,05 минуты"
    override val tl208Decay =
        "β⁻ → Pb-208; в ряду Th-232 через Tl-208 идёт лишь 35,9 % " +
            "распадов Bi-212, поэтому в пересчёте на цепочку выход линий " +
            "примерно втрое меньше приведённого"
    override val tl208Everyday =
        "Конец ториевого ряда: калильные сетки, ториевая оптика, " +
            "монацит, гранитные облицовки. Линия 2615 кэВ — самая жёсткая в " +
            "природном фоне и часто видна в обычной комнате."
    override val tl208Confirmation =
        "Осмысленное совпадение — 583 и 2615 кэВ вместе, " +
            "желательно с 238,6 кэВ от Pb-212. Одна линия 583 кэВ соседствует " +
            "по энергии с другими и сама по себе слаба как довод."
}

object NuclideEn : NuclideStrings {

    override val statusPossibleMatch = "POSSIBLE MATCH"
    override val statusNotConfirmed = "MATCH NOT CONFIRMED"
    override val statusNotEvaluated = "SPECTRUM NOT COMPARED"
    override val statusNotEvaluatedDetail =
        "The card is open as a reference: library data of the nuclide is shown, " +
            "no check by lines was made."

    override val statusAmbiguous = "INSTRUMENT CANNOT SEPARATE THE LINES"

    override fun ambiguousDetail(group: String) =
        "$group: the matched lines lie closer than the instrument's resolution. " +
            "It cannot separate these lines, so no winner is named."

    override val contradictsExpectedLines =
        "The observation contradicts the expected lines of this nuclide."

    override fun matchedOfChecked(found: Int, total: Int) =
        "$found of $total checked lines matched."

    override val notEnoughToConfirm = "That is not enough to confirm it."

    override fun singleLineNuclide(nuclide: String) =
        "$nuclide has a single gamma line — there is no second line to cross-check it " +
            "against, and that uncertainty stays with any single-energy match."

    override fun onlyOneLineCheckable(nuclide: String) =
        "Only one line of $nuclide could be checked: there is nothing to judge the others " +
            "by in this spectrum — they are outside the instrument scale or too weak to tell " +
            "apart."
    override val multiLineStronger =
        "Several lines agree with each other — stronger than one line, but not " +
            "a confirmation."

    override fun missingStrongLine(matched: String, missing: String) =
        "A peak near $matched keV matched, but the more intense $missing keV line " +
            "is not resolved in the spectrum."

    override val limits =
        "A match in peak energy does not identify a nuclide on its own. Close " +
            "lines can be indistinguishable at the energy resolution of the " +
            "detector; the result depends on statistics and calibration."

    override val labelOrigin = "origin"
    override val labelHalfLife = "T½"
    override val labelDecay = "decay"
    override val sectionAbout = "About the nuclide"
    override val sectionEveryday = "Where it is met"
    override val sectionConfirmation = "What would strengthen the hypothesis"
    override val sectionLimitation = "Limitation"
    override val allLinesLabel = "All gamma lines"
    override val close = "Close"

    override val originNatural = "natural"
    override val originArtificial = "artificial"

    override fun originWithChain(origin: String, chain: String) = "$origin · $chain series"

    override fun gammaLine(energy: String, intensity: String) =
        "$energy keV · $intensity % per decay"

    override val sectionLineCheck = "Check by lines"
    override val unitKeV = "keV"

    override val columnLine = "line"
    override val columnYield = "yield"
    override val columnResult = "result"

    override val yieldNote =
        "Yield is the probability of gamma emission per decay of this nuclide; " +
            "the percentages of different lines need not add up to 100 %."

    override fun lineYield(percent: String) = "$percent %"
    override val lineMatched = "matched"
    override val lineNotFound = "not found"
    override val lineTooWeak = "weak, cannot be told apart"
    override val lineNotEvaluated = "not checked"
    override val lineUndetermined = "not found; whether to expect it cannot be assessed"
    override val lineOutOfScale = "beyond the instrument scale"

    override fun peakDelta(measured: String, delta: String) =
        "peak $measured keV · ΔE $delta keV"

    override val lineShowHint = "Tapping a row shows where that line falls on the spectrum."
    override fun lineShowAction(energy: String) = "Show $energy keV on the spectrum"

    override fun bulletOtherLines(lines: String) =
        "other lines of this nuclide matching as well: $lines"

    override fun bulletChainLines(lines: String) =
        "compatible lines of the same decay series: $lines"

    override val bulletHoldsUp =
        "the matched lines holding up over a longer accumulation"

    override val bulletNoContradiction =
        "no contradiction with the more intense lines of the nuclide"

    override val bulletMoreStatistics =
        "more statistics in these energy regions — a longer accumulation"

    override fun lineDataSource(sources: String) = "Line data: $sources"



    override val sourceEnsdf = "ENSDF (IAEA Live Chart / NNDC NuDat 3)"
    override val sourceDdep = "DDEP/LNHB"


    override fun netAreaRatio(fromKeV: String, toKeV: String, value: String, sigma: String) =
        "Net-area ratio $fromKeV/$toKeV keV: $value ± $sigma"

    override fun expectedByYield(value: String) =
        "By the tabulated yields: $value — with no correction for detector efficiency"

    override val efficiencyNotCalibrated =
        "An exact comparison of the line ratio is limited: the relative efficiency " +
            "of the detector at these energies is not calibrated, so the observed " +
            "and the tabulated ratios are shown separately."


    override val ac228Name = "actinium-228"
    override val ac228HalfLife = "6.15 h"
    override val ac228Decay = "β⁻ → Th-228"
    override val ac228Everyday =
        "A daughter of the thorium-232 series, so it is met wherever thorium is: " +
            "granite, monazite sand, incandescent mantles, old optics. Its 911 and " +
            "969 keV lines are the most visible in the natural background after the " +
            "1461 keV of potassium."
    override val ac228Confirmation =
        "A meaningful match is 911 and 969 keV together, better with 338 keV and " +
            "with lines of the other members of the series (238.6 keV of Pb-212, " +
            "2615 keV of Tl-208). The lone 911 keV line sits next to the 934 keV of " +
            "Bi-214 from the uranium series."

    override val bi212Name = "bismuth-212"
    override val bi212HalfLife = "60.6 min"
    override val bi212Decay = "β⁻ (64 %) → Po-212, α (36 %) → Tl-208"
    override val bi212Everyday =
        "Another member of the thorium series; in a scintillator spectrum one line " +
            "of it is distinguishable, 727 keV, the rest are weak or covered by " +
            "neighbours."
    override val bi212Confirmation =
        "The 727 keV line means little on its own: what supports it is the rest of " +
            "the series — 238.6 keV of Pb-212, 583 and 2615 keV of Tl-208, 911 keV " +
            "of Ac-228."

    override val ra226Name = "radium-226"
    override val ra226HalfLife = "1600 years"
    override val ra226Decay = "α → Rn-222"
    override val ra226Everyday =
        "The equilibrium part of the uranium series: building stone, phosphogypsum, " +
            "old luminous dials. Its continuation in the air is radon and the radon " +
            "daughters."
    override val ra226Confirmation =
        "Its own 186 keV line is weak and indistinguishable from the 185.7 keV of " +
            "uranium-235: that line alone cannot choose between them. The series is " +
            "spoken for by the daughters — 352 keV of Pb-214 and 609 keV of Bi-214."

    override val u235Name = "uranium-235"
    override val u235HalfLife = "7.04·10⁸ years"
    override val u235Decay = "α → Th-231"
    override val u235Everyday =
        "0.72 % of natural uranium: uranium ore, some mineral samples. In an " +
            "ordinary room its lines do not appear."
    override val u235Confirmation =
        "The main 185.7 keV line coincides with the 186.2 keV of radium-226, and the " +
            "instrument does not separate them. The set does: 143.8 and 205.3 keV " +
            "belong to uranium only, and the radon-series lines to radium only."

    override val la138Name = "lanthanum-138"
    override val la138HalfLife = "1.02·10¹¹ years"
    override val la138Decay = "electron capture (66 %) → Ba-138, β⁻ (34 %) → Ce-138"
    override val la138Everyday =
        "0.09 % of natural lanthanum: rare-earth minerals (monazite, bastnäsite), " +
            "polishing powders, some lamps and catalysts. The self-activity of " +
            "lanthanum-based scintillators does not concern this instrument — its " +
            "crystal is a different one."
    override val la138Confirmation =
        "A meaningful match is 1436 and 789 keV together, in a ratio of about 2:1. " +
            "The 1436 keV line sits close to the 1461 keV of potassium-40, and on " +
            "this instrument the two separate only after a long accumulation."

    override val cs134Name = "caesium-134"
    override val cs134HalfLife = "2.06 years"
    override val cs134Decay = "β⁻ → Ba-134"
    override val cs134Everyday =
        "Artificial: it appears together with caesium-137 in fresh releases and in " +
            "samples from contaminated areas. Its short half-life makes the ratio to " +
            "caesium-137 a measure of the age of the release."
    override val cs134Confirmation =
        "A meaningful match is 605 and 796 keV together — their yields are nearly " +
            "equal. The 605 keV line neighbours the 609 keV of Bi-214 from the " +
            "natural series, so without the second line there is no conclusion."

    override val k40Name = "potassium-40"
    override val k40HalfLife = "1,248·10⁹ years"
    override val k40Decay =
        "β⁻ (89,3 %) → Ca-40; electron capture (10,7 %) → Ar-40, the " +
            "1460,8 keV photon is born in exactly this branch"
    override val k40Everyday =
        "A permanent admixture of natural potassium — 0,0117 % of its atoms, " +
            "and the fraction does not depend on where the potassium came from. " +
            "That is why the 1461 keV line shows up from salt substitutes and " +
            "potassium-chloride fertilisers, from bananas and other potassium-rich " +
            "food, from granite, concrete, ash and from the human body itself."
    override val k40Confirmation =
        "K-40 has no other lines within the instrument's range, so the match " +
            "cannot be supported by a second line. An indirect argument is the peak " +
            "holding steady over a long accumulation and growing next to " +
            "potassium-rich material."

    override val cs137Name = "caesium-137"
    override val cs137HalfLife = "30,08 years"
    override val cs137Decay =
        "β⁻ → Ba-137m (94,7 %); the metastable barium goes to the ground state " +
            "in 2,55 min and emits 661,7 keV"
    override val cs137Everyday =
        "A fission product: global traces of atmospheric tests and of accidents " +
            "in the upper soil layer, in forest mushrooms and game of affected " +
            "areas. As a sealed source it is met in level gauges, density gauges " +
            "and calibration sets."
    override val cs137Confirmation =
        "The only prominent line, so a single peak is not enough. A meaningful " +
            "argument is a comparison with the recorded reference background of " +
            "the same place and a repeat on another accumulation."

    override val co60Name = "cobalt-60"
    override val co60HalfLife = "5,27 years"
    override val co60Decay =
        "β⁻ → Ni-60; both lines are emitted as a cascade in almost every decay"
    override val co60Everyday =
        "Industrial radiography and sterilisation, medical teletherapy units, " +
            "calibration sources; occasionally it ends up in remelted scrap metal."
    override val co60Confirmation =
        "The 1173 and 1333 keV lines are born as a cascade, so a meaningful match " +
            "requires BOTH, and of roughly equal area. A lone bump near 1173 keV " +
            "is not Co-60."

    override val i131Name = "iodine-131"
    override val i131HalfLife = "8,03 days"
    override val i131Decay = "β⁻ → Xe-131"
    override val i131Everyday =
        "A medical isotope: diagnostics and treatment of the thyroid. Someone " +
            "who has recently had the procedure stays a source for several days — " +
            "this is the most common everyday encounter with it, public transport " +
            "included."
    override val i131Confirmation =
        "The second line at 637 keV is weak, but its absence next to a strong " +
            "364 keV is an argument against. The eight-day half-life is checkable " +
            "only by a SERIES of comparable measurements: one accumulation says " +
            "nothing about a decline."

    override val am241Name = "americium-241"
    override val am241HalfLife = "432,6 years"
    override val am241Decay = "α → Np-237, accompanied by a 59,5 keV photon"
    override val am241Everyday =
        "Ionisation smoke detectors are the most widespread household source; " +
            "also industrial thickness and density gauges."
    override val am241Confirmation =
        "59,5 keV lies where the CsI(Tl) response and the factory energy " +
            "calibration are least accurate, so a low-energy match deserves " +
            "particular doubt. There are no other lines within the instrument's " +
            "range."

    override val bi214Name = "bismuth-214"
    override val bi214HalfLife = "19,9 minutes"
    // Происхождение уже сказано строкой происхождения — не повторяем.
    override val bi214Decay = "β⁻ → Po-214"
    override val bi214Everyday =
        "A short-lived product of the radon-222 chain. Its gamma lines can be seen " +
            "in the natural background — above all where radon decay products are " +
            "in the air: cellars, basements, lower floors."
    override val bi214Confirmation =
        "The radon chain is recognised by SEVERAL lines at once — 609, 1120 and " +
            "1765 keV together with 352 keV from Pb-214. A decline over time means " +
            "something only in a series of comparable measurements under known " +
            "conditions; one accumulation cannot check it."

    override val pb214Name = "lead-214"
    override val pb214HalfLife = "26,9 minutes"
    override val pb214Decay = "β⁻ → Bi-214"
    override val pb214Everyday =
        "The same radon series as Bi-214: basements and cellars, granite and " +
            "tuff, the air after rain."
    override val pb214Confirmation =
        "It goes in pair with Bi-214, so 352 keV without 609 keV looks odd. Both " +
            "lines together are a far more meaningful argument than one peak; a " +
            "decline over time can only be read from a series of measurements."

    override val pb212Name = "lead-212"
    override val pb212HalfLife = "10,64 hours"
    override val pb212Decay = "β⁻ → Bi-212"
    override val pb212Everyday =
        "The thorium series, thoron (Rn-220) branch: old incandescent mantles, " +
            "thoriated optical glass of old lenses, monazite sand, some welding " +
            "electrodes, and ordinary granite as well."
    override val pb212Confirmation =
        "The thorium chain is recognised by 238,6 keV TOGETHER with 583 and " +
            "2615 keV from Tl-208. The 2615 keV line stands apart in the spectrum " +
            "and is therefore the most telling one."

    override val tl208Name = "thallium-208"
    override val tl208HalfLife = "3,05 minutes"
    override val tl208Decay =
        "β⁻ → Pb-208; in the Th-232 series only 35,9 % of Bi-212 decays go " +
            "through Tl-208, so per decay of the chain the line yields are about " +
            "three times lower than the ones listed"
    override val tl208Everyday =
        "The end of the thorium series: incandescent mantles, thoriated optics, " +
            "monazite, granite cladding. The 2615 keV line is the hardest one in " +
            "the natural background and is often seen in an ordinary room."
    override val tl208Confirmation =
        "A meaningful match is 583 and 2615 keV together, preferably with " +
            "238,6 keV from Pb-212. The single 583 keV line has neighbours in " +
            "energy and is weak on its own as an argument."
}

val NuclideCatalogue = AreaCatalogue(ru = NuclideRu, en = NuclideEn)

/** Все строки области — для проверки, действующей на каждую формулировку. */
fun NuclideStrings.allTexts(): List<String> = listOf(
    ac228Name, ac228HalfLife, ac228Decay, ac228Everyday, ac228Confirmation,
    bi212Name, bi212HalfLife, bi212Decay, bi212Everyday, bi212Confirmation,
    ra226Name, ra226HalfLife, ra226Decay, ra226Everyday, ra226Confirmation,
    u235Name, u235HalfLife, u235Decay, u235Everyday, u235Confirmation,
    la138Name, la138HalfLife, la138Decay, la138Everyday, la138Confirmation,
    cs134Name, cs134HalfLife, cs134Decay, cs134Everyday, cs134Confirmation,
    statusPossibleMatch, statusNotConfirmed, statusNotEvaluated, statusNotEvaluatedDetail,
    statusAmbiguous, ambiguousDetail("Pb-214 / I-131"), contradictsExpectedLines,
    matchedOfChecked(1, 3), notEnoughToConfirm, multiLineStronger,
    singleLineNuclide("K-40"), onlyOneLineCheckable("Bi-214"),
    missingStrongLine("1120", "609"),
    limits,
    labelOrigin, labelHalfLife, labelDecay,
    sectionAbout, sectionEveryday, sectionConfirmation, sectionLimitation,
    allLinesLabel, close,
    originNatural, originArtificial,
    originWithChain(originNatural, "Th-232"),
    gammaLine("609,3", "45,5"),
    sectionLineCheck, unitKeV, columnLine, columnYield, columnResult, yieldNote,
    lineYield("45,5"), lineMatched, lineNotFound, lineTooWeak, lineNotEvaluated,
    lineUndetermined, lineOutOfScale,
    peakDelta("1109,3", "−11,0"),
    lineShowHint, lineShowAction("661,7"),
    bulletOtherLines("609,3 кэВ"), bulletChainLines("Pb-214 351,9 кэВ"),
    bulletHoldsUp, bulletNoContradiction, bulletMoreStatistics,
    lineDataSource(sourceEnsdf),
    sourceEnsdf, sourceDdep,
    netAreaRatio("609", "1120", "3,05", "0,42"),
    expectedByYield("3,05"), efficiencyNotCalibrated,
) + nuclideTexts()

/** Тексты всех девяти карточек: имя, период, распад, где встречается, чем подтверждать. */
private fun NuclideStrings.nuclideTexts(): List<String> = listOf(
    k40Name, k40HalfLife, k40Decay, k40Everyday, k40Confirmation,
    cs137Name, cs137HalfLife, cs137Decay, cs137Everyday, cs137Confirmation,
    co60Name, co60HalfLife, co60Decay, co60Everyday, co60Confirmation,
    i131Name, i131HalfLife, i131Decay, i131Everyday, i131Confirmation,
    am241Name, am241HalfLife, am241Decay, am241Everyday, am241Confirmation,
    bi214Name, bi214HalfLife, bi214Decay, bi214Everyday, bi214Confirmation,
    pb214Name, pb214HalfLife, pb214Decay, pb214Everyday, pb214Confirmation,
    pb212Name, pb212HalfLife, pb212Decay, pb212Everyday, pb212Confirmation,
    tl208Name, tl208HalfLife, tl208Decay, tl208Everyday, tl208Confirmation,
)
