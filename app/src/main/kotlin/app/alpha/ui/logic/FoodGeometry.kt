package app.alpha.ui.logic

import app.alpha.ui.text.FoodRu
import app.alpha.ui.text.FoodStrings

/**
 * Как стоял образец — пресетом, а не сочинением.
 *
 * Геометрия это то, что обязано повториться при следующем измерении того же
 * продукта: одна и та же ёмкость, одно и то же положение прибора. Свободная
 * строка «банка, сбоку, вплотную» повторяется приблизительно, а выбранный
 * пресет — буквально, и его же можно показать перед следующим прогоном.
 *
 * Список короткий намеренно: это не каталог посуды, а несколько типовых
 * положений плюс «своя» для всего остального. Пресет НЕ даёт эффективности
 * измерения и не превращает счёт в беккерели — он лишь фиксирует условия,
 * чтобы два измерения были сравнимы между собой.
 */
enum class FoodGeometry(val code: String) {
    JAR_HALF_LITRE("jar_05"),
    JAR_LITRE("jar_1"),
    CUP("cup"),
    BAG("bag"),
    PLATE("plate"),
    CUSTOM("custom"),
    ;

    fun label(s: FoodStrings = FoodRu): String = when (this) {
        JAR_HALF_LITRE -> s.geometryJarHalf
        JAR_LITRE -> s.geometryJarLitre
        CUP -> s.geometryCup
        BAG -> s.geometryBag
        PLATE -> s.geometryPlate
        CUSTOM -> s.geometryCustom
    }

    fun hint(s: FoodStrings = FoodRu): String = when (this) {
        JAR_HALF_LITRE, JAR_LITRE -> s.geometryJarHint
        CUP -> s.geometryCupHint
        BAG -> s.geometryBagHint
        PLATE -> s.geometryPlateHint
        CUSTOM -> s.geometryCustomHint
    }

    companion object {
        fun of(code: String?): FoodGeometry =
            entries.firstOrNull { it.code == code } ?: CUSTOM
    }
}
