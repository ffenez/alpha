package app.alpha.ui.logic

import app.alpha.data.db.ProfileEntity
import app.alpha.ui.text.RuStrings
import app.alpha.ui.text.Strings

/**
 * Имя места НА ЭКРАНЕ.
 *
 * Шесть готовых мест (spec §3.1) и две служебные роли придумало приложение, а
 * не человек, поэтому они переводятся вместе с языком интерфейса: английский
 * экран с местом «Дом» человек не выбирал. Имя, введённое руками, не
 * переводится никогда — совпадение с готовым именем означает, что человек
 * выбрал именно его.
 *
 * Перевод делается при показе, а не в базе: там остаётся имя, под которым
 * место создано, и записи истории продолжают ссылаться на него при любом
 * переключении языка.
 */
object ProfileName {

    /** Готовое имя на любом из языков → имя на текущем. */
    fun label(name: String, s: Strings = RuStrings): String = when (name) {
        "Дом", "Home" -> s.presetHome
        "Офис", "Office" -> s.presetOffice
        "Дача", "Cottage" -> s.presetCottage
        "Родители", "Parents" -> s.presetParents
        "В пути", "In transit" -> s.presetTransit
        "Без места", "No place" -> s.presetNoPlace
        else -> name
    }

    /**
     * Служебные роли называются по роли, а не по хранимому имени: строку
     * «в пути» приложение заводит само и человек её не вводил.
     */
    fun of(profile: ProfileEntity, s: Strings = RuStrings): String = when (profile.role) {
        ProfileEntity.ROLE_TRANSIT -> s.presetTransit
        ProfileEntity.ROLE_NO_PLACE -> s.presetNoPlace
        else -> label(profile.name, s)
    }
}
