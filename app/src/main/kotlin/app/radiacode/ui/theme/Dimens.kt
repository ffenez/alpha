package app.radiacode.ui.theme

import androidx.compose.ui.unit.dp

/**
 * «Научный терминал» geometry (design-language.md): 14dp cards, 9dp chips,
 * 10dp buttons, 1dp hairlines. Spacing is a 4dp scale.
 */
object Dimens {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp

    val radiusCard = 14.dp
    val radiusButton = 10.dp
    val radiusChip = 9.dp
    /** Inner radius of a selected segment inside a 9dp segmented track. */
    val radiusSegment = 7.dp

    val border = 1.dp

    /** Minimum touch target (field use: thumb, gloves). */
    val touchTarget = 48.dp

    /**
     * Границы встроенного поля спектра. Само поле — доля высоты экрана
     * ([app.radiacode.ui.logic.SpectrumPlot.fieldHeightDp]): спектр это главная
     * картинка вкладки, и на большом экране ей положено больше высоты, чем
     * фиксированные 170 dp. Границы держат края: на мелком экране поле не
     * съедает таблицу пиков, на планшете не растягивается на всю страницу.
     */
    val spectrumFieldMin = 200.dp
    val spectrumFieldMax = 320.dp
}
