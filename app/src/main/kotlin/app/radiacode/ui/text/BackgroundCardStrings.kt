package app.radiacode.ui.text

/**
 * Короткая карточка записанного фона Поиска (ТЗ §10).
 *
 * Каталог существует отдельно от [SearchStrings] потому, что это ДРУГОЙ
 * уровень изложения одного и того же: здесь живут строки рабочего экрана —
 * значение, момент, длительность и одна строка с названной причиной, — а
 * развёрнутые объяснения (`proposalAged`, `backgroundDetail` и остальные)
 * остаются в `SearchStrings` и показываются под «i».
 *
 * Правила области:
 *
 * - Причина непригодности НАЗЫВАЕТСЯ всегда. «Фон непригоден» без причины
 *   заставляет человека гадать, что чинить; строка короткая, но причина в ней
 *   есть — устарел, другой профиль, другой прибор, каким был сам замер.
 * - Ни одна строка не обещает, что сравнение верно: карточка сообщает
 *   состояние точки отсчёта, а не состояние излучения.
 * - Действие названо действием («Обновить фон»), а не понятием.
 */
interface BackgroundCardStrings {

    /** Главная строка: «Фон: 24,1 имп/с». */
    fun level(rate: String): String

    /** Основание: «Записан в 15:04 · измерение 45 с». */
    /**
     * «Записан 12.08 в 14:30 · измерение 45 с».
     *
     * День назван вместе со временем: по возрасту фона видно, годится ли он
     * для сравнения, а «в 14:30» без даты этого не говорит — вчерашний фон и
     * сегодняшний выглядели одинаково. Длительность самого замера отсюда
     * убрана: она одинакова у всех записей и ничего не решает.
     */
    fun recordedAt(day: String, time: String): String

    /** Фона ещё нет — карточка учит первому действию. */
    val noRecord: String
    fun noRecordHint(seconds: Int): String

    // ------------------------------------------- одна строка с причиной
    val agedLine: String
    fun profileChangedLine(profile: String?): String
    val deviceChangedLine: String
    val shortLine: String
    val gappyLine: String
    val restlessLine: String

    // -------------------------------------------------------- действия
    val refresh: String
    fun measure(seconds: Int): String

    /** Подпись кнопки второго уровня — там же, где полное объяснение. */
    val details: String
}

object BackgroundCardRu : BackgroundCardStrings {

    override fun level(rate: String) = "Фон: $rate имп/с"

    override fun recordedAt(day: String, time: String) = "Записан $day в $time"

    override val noRecord = "Фон не записан"

    override fun noRecordHint(seconds: Int) =
        "Отойдите от предполагаемого источника и замерьте фон — $seconds с неподвижно."

    override val agedLine = "Фон записан давно · для точного сравнения лучше обновить"

    override fun profileChangedLine(profile: String?) =
        "Фон записан в другом профиле" + (profile?.let { " («$it»)" } ?: "") + " · обновите его"

    override val deviceChangedLine = "Фон записан другим прибором · обновите его"

    override val shortLine = "Замер фона не был закончен · обновите его"

    override val gappyLine = "В замере фона были пропуски потока · обновите его"

    override val restlessLine = "Во время замера прибор не был неподвижен · обновите его"

    override val refresh = "Обновить фон"

    override fun measure(seconds: Int) = "Замерить фон · $seconds с"

    override val details = "Подробнее"
}

object BackgroundCardEn : BackgroundCardStrings {

    override fun level(rate: String) = "Background: $rate counts/s"

    override fun recordedAt(day: String, time: String) = "Recorded on $day at $time"

    override val noRecord = "No background recorded"

    override fun noRecordHint(seconds: Int) =
        "Step away from the suspected source and record the background — $seconds s, held still."

    override val agedLine = "Recorded a while ago · refresh it for an accurate comparison"

    override fun profileChangedLine(profile: String?) =
        "Recorded in another profile" + (profile?.let { " («$it»)" } ?: "") + " · refresh it"

    override val deviceChangedLine = "Recorded with another instrument · refresh it"

    override val shortLine = "The background run did not finish · refresh it"

    override val gappyLine = "The background run lost stream time · refresh it"

    override val restlessLine = "The instrument was not held still during the run · refresh it"

    override val refresh = "Refresh the background"

    override fun measure(seconds: Int) = "Record the background · $seconds s"

    override val details = "Details"
}

val BackgroundCardCatalogue = AreaCatalogue(ru = BackgroundCardRu, en = BackgroundCardEn)

/** Все строки области — для проверок, действующих на каждую формулировку. */
fun BackgroundCardStrings.allTexts(): List<String> = listOf(
    level("24,1"), recordedAt("12.08", "15:04"), noRecord, noRecordHint(45),
    agedLine, profileChangedLine("Дом"), profileChangedLine(null), deviceChangedLine,
    shortLine, gappyLine, restlessLine,
    refresh, measure(45), details,
)
