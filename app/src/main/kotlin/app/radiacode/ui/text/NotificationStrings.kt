package app.radiacode.ui.text

/**
 * Тексты уведомлений и системных каналов.
 *
 * Отдельный каталог, а не общий [Strings], по практической причине: их пишет
 * СЕРВИС, куда композиционный `LocalStrings` не приходит — язык он читает из
 * настроек сам. Держать эти пять строк вместе с экранными значило бы тащить
 * в сервис весь каталог интерфейса.
 *
 * Правила честности те же, что на экранах: заголовок тревоги говорит, что
 * УРОВЕНЬ ИЗМЕНИЛСЯ, а не что стало опасно, и рядом стоят оба числа.
 */
interface NotificationStrings {
    val measurementChannel: String
    val alarmChannel: String
    val alarmChannelDescription: String
    val levelChanged: String
    fun nowRate(rate: String): String
    fun usuallyUpTo(rate: String): String

    /** Подпись источника «эксперимента» в статусе службы: экран Поиска. */
    val searchSource: String
}

object NotificationRu : NotificationStrings {
    override val measurementChannel = "Измерение"
    override val alarmChannel = "Тревога"
    override val alarmChannelDescription =
        "Устойчивое превышение уровня, подтверждённое по величине и длительности"
    override val levelChanged = "Уровень радиации изменился"

    override fun nowRate(rate: String) = "Сейчас $rate"

    override fun usuallyUpTo(rate: String) = " · обычно здесь до $rate"

    override val searchSource = "Поиск"
}

object NotificationEn : NotificationStrings {
    override val measurementChannel = "Measurement"
    override val alarmChannel = "Alarm"
    override val alarmChannelDescription =
        "A sustained excess of the level, confirmed by both magnitude and duration"
    override val levelChanged = "The radiation level changed"

    override fun nowRate(rate: String) = "Now $rate"

    override fun usuallyUpTo(rate: String) = " · usually up to $rate here"

    override val searchSource = "Search"
}

val NotificationCatalogue = AreaCatalogue(ru = NotificationRu, en = NotificationEn)

/** Все строки области — для проверки, действующей на каждую формулировку. */
fun NotificationStrings.allTexts(): List<String> = listOf(
    measurementChannel, alarmChannel, alarmChannelDescription, levelChanged,
    nowRate("0,30 мкЗв/ч"), usuallyUpTo("0,18 мкЗв/ч"), searchSource,
)
