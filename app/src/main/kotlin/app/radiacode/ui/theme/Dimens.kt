package app.radiacode.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 4dp pixel grid (design-language.md): every spacing is a multiple of [grid];
 * radii are always 0. Frames are 3dp, hard chunk shadows are offset 5dp
 * right-down with no blur.
 */
object PixelDimens {
    val grid = 4.dp
    val gridHalf = 2.dp
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp

    val frame = 3.dp
    val shadowOffset = 5.dp

    /** Minimum touch target (field use: thumb, gloves). */
    val touchTarget = 48.dp
}
